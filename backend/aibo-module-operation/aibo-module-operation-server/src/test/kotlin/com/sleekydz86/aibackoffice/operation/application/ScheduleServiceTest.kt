package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.operation.domain.Channel
import com.sleekydz86.aibackoffice.operation.domain.ScheduleSlot
import com.sleekydz86.aibackoffice.operation.domain.SlotStatus
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ChannelRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ContentExposureStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IdempotencyStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ScheduleSlotRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class ScheduleServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)
    private val channel = Channel("TVN", "tvN", "19").apply { id = 1 }

    private val channels = mock<ChannelRepository>()
    private val slots = mock<ScheduleSlotRepository>()
    private val exposures = mock<ContentExposureStore>()
    private val audit = mock<AuditRecorder>()
    private val idempotency = IdempotencyService(mock<IdempotencyStore>(), JsonMapper.builder().build())
    private val service = ScheduleService(channels, slots, exposures, idempotency, audit, clock)

    private fun user(
        service: Boolean = false,
        perms: Set<String> = setOf(Permissions.SCHEDULE_READ, Permissions.SCHEDULE_DRAFT, Permissions.SCHEDULE_PUBLISH),
        scopes: Set<String> = setOf("TVN"),
    ) = LoginUser(
        userId = if (service) "svc:mcp" else "7",
        name = "편성 담당",
        team = null,
        roles = emptySet(),
        perms = perms,
        scopes = scopes,
        permissionVersion = 0,
        tokenId = null,
        tokenType = if (service) TokenType.SERVICE else TokenType.USER,
        onBehalfOf = if (service) "7" else null,
        actorType = ActorType.HUMAN,
        rawToken = "token",
    )

    private fun draftSlot(status: SlotStatus = SlotStatus.DRAFT, approvalId: Long? = null) = ScheduleSlot(
        channelId = 1,
        contentId = 3,
        episodeId = null,
        contentRating = "15",
        startAt = now.plusDays(1).withHour(20),
        endAt = now.plusDays(1).withHour(21),
        createdBy = "7",
        createdAt = now,
    ).apply {
        id = 10
        this.status = status
        this.approvalId = approvalId
    }

    private fun command(startAt: LocalDateTime, minutes: Long = 60, channelCode: String = "TVN") = SlotDraftCommand(
        channelCode = channelCode,
        contentId = 3,
        contentRating = "15",
        startAt = startAt.atZone(Zones.SEOUL).toOffsetDateTime(),
        endAt = startAt.plusMinutes(minutes).atZone(Zones.SEOUL).toOffsetDateTime(),
    )

    private fun stubChannel() {
        whenever(channels.findByCode("TVN")).thenReturn(channel)
        whenever(channels.findById(1)).thenReturn(Optional.of(channel))
        whenever(channels.findLockedById(1)).thenReturn(channel)
    }

    @Test
    @DisplayName("사람 토큰으로는 승인 번호가 있어도 게시할 수 없다")
    fun humanCannotPublishDirectly() {
        // when
        val error = assertThrows<BusinessException> { service.publish(user(), 10, 55, null) }

        // then
        assertEquals(ErrorCode.APPROVAL_REQUIRED, error.code)
        verify(slots, never()).findById(any())
    }

    @Test
    @DisplayName("승인 번호 없이 게시하면 서비스 토큰이어도 거절한다")
    fun publishNeedsApprovalId() {
        // when
        val error = assertThrows<BusinessException> { service.publish(user(service = true), 10, null, null) }

        // then
        assertEquals(ErrorCode.APPROVAL_REQUIRED, error.code)
    }

    @Test
    @DisplayName("승인된 실행이면 충돌을 다시 검사한 뒤 게시하고 감사 기록을 남긴다")
    fun publishesApprovedDraft() {
        // given
        stubChannel()
        val slot = draftSlot()
        whenever(slots.findById(10)).thenReturn(Optional.of(slot))
        whenever(slots.findOverlapping(eq(1), any(), any(), eq(SlotStatus.CANCELED))).thenReturn(listOf(slot))

        // when
        val view = service.publish(user(service = true), 10, 55, null)

        // then
        assertEquals(SlotStatus.PUBLISHED, view.status)
        assertEquals(55L, view.approvalId)
        verify(audit).record(any(), eq("schedule.publish"), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(55L))
    }

    @Test
    @DisplayName("같은 승인 번호로 다시 게시하면 바꾸지 않고 처음 결과를 돌려준다")
    fun publishIsIdempotentByApprovalId() {
        // given
        stubChannel()
        whenever(slots.findById(10)).thenReturn(Optional.of(draftSlot(SlotStatus.PUBLISHED, 55)))

        // when
        val view = service.publish(user(service = true), 10, 55, null)

        // then
        assertEquals(SlotStatus.PUBLISHED, view.status)
        verify(channels, never()).findLockedById(any())
        verify(audit, never()).record(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @DisplayName("담당하지 않는 채널의 편성은 게시할 수 없다")
    fun publishChecksChannelScope() {
        // given
        stubChannel()
        whenever(slots.findById(10)).thenReturn(Optional.of(draftSlot()))

        // when
        val error = assertThrows<BusinessException> { service.publish(user(service = true, scopes = setOf("OCN")), 10, 55, null) }

        // then
        assertEquals(ErrorCode.SCOPE_DENIED, error.code)
    }

    @Test
    @DisplayName("시작까지 두 시간이 안 남은 초안은 승인을 거쳐야 한다")
    fun draftCloseToStartNeedsApproval() {
        // given
        stubChannel()

        // when
        val error = assertThrows<BusinessException> { service.createDraft(user(), command(now.plusMinutes(90)), null, null) }

        // then
        assertEquals(ErrorCode.APPROVAL_REQUIRED, error.code)
        verify(slots, never()).save(any<ScheduleSlot>())
    }

    @Test
    @DisplayName("담당하지 않는 채널에는 초안을 만들 수 없다")
    fun draftChecksChannelScope() {
        // given
        stubChannel()

        // when
        val error = assertThrows<BusinessException> {
            service.createDraft(user(scopes = setOf("OCN")), command(now.plusDays(1)), null, null)
        }

        // then
        assertEquals(ErrorCode.SCOPE_DENIED, error.code)
    }

    @Test
    @DisplayName("같은 채널에서 시간이 겹치면 초안을 저장하지 않고 409로 거절한다")
    fun draftRejectsOverlap() {
        // given
        stubChannel()
        whenever(slots.findOverlapping(eq(1), any(), any(), eq(SlotStatus.CANCELED))).thenReturn(listOf(draftSlot()))

        // when
        val error = assertThrows<BusinessException> {
            service.createDraft(user(), command(now.plusDays(1).withHour(20).withMinute(30)), null, null)
        }

        // then
        assertEquals(ErrorCode.CONFLICT, error.code)
        verify(slots, never()).save(any<ScheduleSlot>())
    }

    @Test
    @DisplayName("끝나는 시각이 시작보다 이르면 400으로 거절한다")
    fun draftRejectsReversedTime() {
        // given
        stubChannel()
        val start = now.plusDays(1).withHour(20).atZone(Zones.SEOUL).toOffsetDateTime()
        val reversed = SlotDraftCommand("TVN", 3, null, "15", start, start.minusMinutes(30))

        // when
        val error = assertThrows<BusinessException> { service.createDraft(user(), reversed, null, null) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
    }

    @Test
    @DisplayName("규칙에 맞는 초안은 저장하고 감사 기록을 남긴다")
    fun createsDraft() {
        // given
        stubChannel()
        whenever(slots.findOverlapping(eq(1), any(), any(), eq(SlotStatus.CANCELED))).thenReturn(emptyList())
        whenever(slots.save(any<ScheduleSlot>())).thenAnswer { (it.arguments[0] as ScheduleSlot).apply { id = 11 } }
        val start = now.plusDays(1).withHour(19)

        // when
        val view = service.createDraft(user(), command(start), null, null)

        // then
        assertEquals(11L, view.id)
        assertEquals(SlotStatus.DRAFT, view.status)
        assertEquals("TVN", view.channelCode)
        assertEquals(OffsetDateTime.of(start, Zones.SEOUL.rules.getOffset(start)), view.startAt)
        verify(audit).record(any(), eq("schedule.draft.create"), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
}
