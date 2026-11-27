package com.sleekydz86.aibackoffice.framework.audit.core

import com.sleekydz86.aibackoffice.framework.audit.config.AiboAuditProperties
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AuditOutboxRelayTest {

    private val properties = AiboAuditProperties(maxRetries = 3)
    private val rows = listOf(OutboxRow(1, "{\"a\":1}", 0), OutboxRow(2, "{\"a\":2}", 2))

    @Test
    @DisplayName("governance가 받으면 보낸 기록을 SENT로 바꾼다")
    fun marksSentWhenGovernanceAccepts() {
        // given
        val store = mock<AuditOutboxStore> { on { claim(any(), any()) } doReturn rows }
        val sink = mock<AuditSink>()

        // when
        AuditOutboxRelay(store, sink, properties).relay()

        // then
        verify(sink).send(listOf("{\"a\":1}", "{\"a\":2}"))
        verify(store).markSent(listOf(1L, 2L))
    }

    @Test
    @DisplayName("governance가 죽어 있으면 기록을 지우지 않고 다시 보낼 시각을 잡는다. 상한을 넘으면 FAILED로 멈춘다")
    fun keepsEventsWhenGovernanceIsDown() {
        // given
        val store = mock<AuditOutboxStore> { on { claim(any(), any()) } doReturn rows }
        val sink = mock<AuditSink> { on { send(any()) } doThrow IllegalStateException("연결 거부") }

        // when
        AuditOutboxRelay(store, sink, properties).relay()

        // then
        verify(store, never()).markSent(any())
        verify(store).markRetry(eq(rows[0]), any(), eq(false))
        verify(store).markRetry(eq(rows[1]), any(), eq(true))
    }

    @Test
    @DisplayName("다시 보내는 간격은 두 배씩 늘고 10분을 넘지 않는다")
    fun backoffDoublesAndCaps() {
        // then
        assertEquals(Duration.ofSeconds(2), AuditOutboxRelay.backoff(1))
        assertEquals(Duration.ofSeconds(8), AuditOutboxRelay.backoff(3))
        assertEquals(Duration.ofMinutes(10), AuditOutboxRelay.backoff(15))
    }
}
