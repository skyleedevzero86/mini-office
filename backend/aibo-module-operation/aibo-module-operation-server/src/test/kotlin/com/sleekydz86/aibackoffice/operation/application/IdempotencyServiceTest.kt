package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IdempotencyStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.StoredResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class IdempotencyServiceTest {

    data class Answer(val value: String)

    private val store = mock<IdempotencyStore>()
    private val service = IdempotencyService(store, JsonMapper.builder().addModule(KotlinModule.Builder().build()).build())
    private val user = LoginUser(
        userId = "7", name = "편성 담당", team = null, roles = emptySet(), perms = emptySet(), scopes = emptySet(),
        permissionVersion = 0, tokenId = null, tokenType = TokenType.USER, onBehalfOf = null,
        actorType = ActorType.HUMAN, rawToken = "token",
    )

    @Test
    @DisplayName("멱등 키가 없으면 기록 없이 그냥 실행한다")
    fun runsWithoutKey() {
        // when
        val result = service.execute(user, null, "op", mapOf("a" to 1), Answer::class.java) { Answer("first") }

        // then
        assertEquals(Answer("first"), result)
        verify(store, never()).find(any())
    }

    @Test
    @DisplayName("처음 온 키는 실행한 뒤 사용자 번호를 붙여 결과를 남긴다")
    fun storesFirstResult() {
        // given
        whenever(store.find("7:key-1")).thenReturn(null)

        // when
        service.execute(user, "key-1", "op", mapOf("a" to 1), Answer::class.java) { Answer("first") }

        // then
        val body = argumentCaptor<String>()
        verify(store).insert(eq("7:key-1"), any(), eq(200), body.capture())
        assertEquals("{\"value\":\"first\"}", body.firstValue)
    }

    @Test
    @DisplayName("같은 키와 같은 내용이 다시 오면 실행하지 않고 처음 결과를 돌려준다")
    fun replaysSameRequest() {
        // given
        val hash = argumentCaptor<String>()
        service.execute(user, "key-1", "op", mapOf("a" to 1), Answer::class.java) { Answer("first") }
        verify(store).insert(eq("7:key-1"), hash.capture(), eq(200), any())
        whenever(store.find("7:key-1")).thenReturn(StoredResponse(hash.firstValue, 200, "{\"value\":\"first\"}"))
        var executed = false

        // when
        val result = service.execute(user, "key-1", "op", mapOf("a" to 1), Answer::class.java) {
            executed = true
            Answer("second")
        }

        // then
        assertEquals(Answer("first"), result)
        assertEquals(false, executed)
    }

    @Test
    @DisplayName("같은 키로 다른 내용이 오면 422로 거절한다")
    fun rejectsDifferentRequestWithSameKey() {
        // given
        whenever(store.find("7:key-1")).thenReturn(StoredResponse("other-hash", 200, "{\"value\":\"first\"}"))

        // when
        val error = assertThrows<BusinessException> {
            service.execute(user, "key-1", "op", mapOf("a" to 2), Answer::class.java) { Answer("second") }
        }

        // then
        assertEquals(ErrorCode.IDEMPOTENCY_MISMATCH, error.code)
    }

    @Test
    @DisplayName("너무 긴 멱등 키는 400으로 거절한다")
    fun rejectsTooLongKey() {
        // when
        val error = assertThrows<BusinessException> {
            service.execute(user, "k".repeat(65), "op", null, Answer::class.java) { Answer("x") }
        }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
    }
}
