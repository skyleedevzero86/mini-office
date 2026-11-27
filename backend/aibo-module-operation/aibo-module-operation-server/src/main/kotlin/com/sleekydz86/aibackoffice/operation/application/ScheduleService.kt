package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.operation.domain.Channel
import com.sleekydz86.aibackoffice.operation.domain.ContentRating
import com.sleekydz86.aibackoffice.operation.domain.ScheduleConflict
import com.sleekydz86.aibackoffice.operation.domain.ScheduleRules
import com.sleekydz86.aibackoffice.operation.domain.ScheduleSlot
import com.sleekydz86.aibackoffice.operation.domain.SlotCandidate
import com.sleekydz86.aibackoffice.operation.domain.SlotStatus
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ChannelRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ContentExposureReader
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ScheduleSlotRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ScheduleSlotSpecs
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScheduleService(
    private val channels: ChannelRepository,
    private val slots: ScheduleSlotRepository,
    private val exposures: ContentExposureReader,
    private val idempotency: IdempotencyService,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun channels(user: LoginUser): List<ChannelView> {
        SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_READ)
        return channels.findAll().sortedBy { it.id }.map(ChannelView::from)
    }

    @Transactional(readOnly = true)
    fun slots(user: LoginUser, filter: SlotFilter, pageable: Pageable): SlotPage {
        SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_READ)
        val visible = visibleChannels(user, filter.channelCode)
        if (visible.isEmpty()) return SlotPage(emptyList(), 0)
        val codes = visible.associate { it.requireId() to it.code }
        val specs = buildList {
            add(ScheduleSlotSpecs.channelIn(codes.keys))
            filter.status?.let { add(ScheduleSlotSpecs.status(it)) }
            filter.from?.let { add(ScheduleSlotSpecs.endsAfter(SeoulTime.toLocal(it))) }
            filter.to?.let { add(ScheduleSlotSpecs.startsBefore(SeoulTime.toLocal(it))) }
        }
        val page = slots.findAll(Specification.allOf(specs), pageable)
        return SlotPage(page.content.map { ScheduleSlotView.from(it, codes.getValue(it.channelId)) }, page.totalElements)
    }

    @Transactional(readOnly = true)
    fun slot(user: LoginUser, slotId: Long): ScheduleSlotView {
        SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_READ)
        val slot = requireSlot(slotId)
        val channel = requireChannelById(slot.channelId)
        SecurityFrameworkUtils.requireChannel(user, channel.code)
        return ScheduleSlotView.from(slot, channel.code)
    }

    @Transactional(readOnly = true)
    fun conflicts(user: LoginUser, query: ConflictQuery): List<ConflictView> {
        SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_READ)
        val channel = requireChannelByCode(query.channelCode)
        SecurityFrameworkUtils.requireChannel(user, channel.code)
        val candidate = candidate(query.contentId, query.contentRating, query.startAt, query.endAt)
        return findConflicts(channel, candidate, query.excludeSlotId).map(ConflictView::from)
    }

    @Transactional
    fun createDraft(user: LoginUser, command: SlotDraftCommand, approvalId: Long?, idempotencyKey: String?): ScheduleSlotView =
        idempotency.execute(user, idempotencyKey, "schedule.draft.create", command, ScheduleSlotView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_DRAFT)
            val channelCode = command.channelCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "채널 코드 channelCode 가 필요합니다.")
            val contentId = command.contentId
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "작품 번호 contentId 가 필요합니다.")
            val startAt = command.startAt
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "시작 시각 startAt 이 필요합니다.")
            val endAt = command.endAt
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "끝나는 시각 endAt 이 필요합니다.")
            val found = requireChannelByCode(channelCode)
            SecurityFrameworkUtils.requireChannel(user, found.code)
            val candidate = candidate(contentId, command.contentRating, startAt, endAt)
            val now = LocalDateTime.now(clock)
            if (!candidate.startAt.isAfter(now)) {
                throw BusinessException(ErrorCode.VALIDATION_FAILED, "이미 지난 시각에는 편성할 수 없습니다.")
            }
            if (candidate.startAt.isBefore(now.plus(APPROVAL_WINDOW))) {
                requireApprovedExecution(user, approvalId, "시작까지 두 시간이 안 남은 편성은 승인을 거쳐야 합니다.")
            }
            val channel = lockChannel(found)
            rejectConflicts(findConflicts(channel, candidate, null))
            val slot = slots.save(
                ScheduleSlot(
                    channelId = channel.requireId(),
                    contentId = candidate.contentId,
                    episodeId = command.episodeId,
                    contentRating = candidate.rating?.code,
                    startAt = candidate.startAt,
                    endAt = candidate.endAt,
                    createdBy = user.effectiveUserId,
                    createdAt = now,
                ),
            )
            slots.flush()
            val view = ScheduleSlotView.from(slot, channel.code)
            audit.record(
                user,
                action = "schedule.draft.create",
                result = AuditResult.SUCCESS,
                resourceType = RESOURCE_TYPE,
                resourceId = view.id.toString(),
                afterValue = view,
                approvalId = approvalId,
            )
            log.info("편성 초안을 만들었습니다. 편성={} 채널={} 작품={}", view.id, channel.code, view.contentId)
            view
        }

    @Transactional
    fun publish(user: LoginUser, slotId: Long, approvalId: Long?, idempotencyKey: String?): ScheduleSlotView =
        idempotency.execute(user, idempotencyKey, "schedule.publish", mapOf("slotId" to slotId, "approvalId" to approvalId), ScheduleSlotView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_PUBLISH)
            val approved = requireApprovedExecution(user, approvalId, "편성 게시는 승인을 거친 실행으로만 할 수 있습니다.")
            val slot = requireSlot(slotId)
            val channel = requireChannelById(slot.channelId)
            SecurityFrameworkUtils.requireChannel(user, channel.code)
            if (slot.alreadyDone(SlotStatus.PUBLISHED, approved)) {
                log.info("같은 승인 번호로 이미 게시한 편성이라 그대로 돌려줍니다. 편성={} 승인={}", slotId, approved)
                return@execute ScheduleSlotView.from(slot, channel.code)
            }
            lockChannel(channel)
            val before = ScheduleSlotView.from(slot, channel.code)
            val candidate = SlotCandidate(slot.contentId, ContentRating.fromCode(slot.contentRating), slot.startAt, slot.endAt)
            rejectConflicts(findConflicts(channel, candidate, slot.id))
            slot.publish(approved)
            slots.flush()
            val after = ScheduleSlotView.from(slot, channel.code)
            audit.record(
                user,
                action = "schedule.publish",
                result = AuditResult.SUCCESS,
                resourceType = RESOURCE_TYPE,
                resourceId = slotId.toString(),
                beforeValue = before,
                afterValue = after,
                approvalId = approved,
            )
            log.info("편성을 게시했습니다. 편성={} 승인={}", slotId, approved)
            after
        }

    @Transactional
    fun cancel(user: LoginUser, slotId: Long, approvalId: Long?, idempotencyKey: String?): ScheduleSlotView =
        idempotency.execute(user, idempotencyKey, "schedule.cancel", mapOf("slotId" to slotId, "approvalId" to approvalId), ScheduleSlotView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.SCHEDULE_PUBLISH)
            val approved = requireApprovedExecution(user, approvalId, "편성 취소는 승인을 거친 실행으로만 할 수 있습니다.")
            val slot = requireSlot(slotId)
            val channel = requireChannelById(slot.channelId)
            SecurityFrameworkUtils.requireChannel(user, channel.code)
            if (slot.alreadyDone(SlotStatus.CANCELED, approved)) {
                log.info("같은 승인 번호로 이미 취소한 편성이라 그대로 돌려줍니다. 편성={} 승인={}", slotId, approved)
                return@execute ScheduleSlotView.from(slot, channel.code)
            }
            val before = ScheduleSlotView.from(slot, channel.code)
            slot.cancel(approved)
            slots.flush()
            val after = ScheduleSlotView.from(slot, channel.code)
            audit.record(
                user,
                action = "schedule.cancel",
                result = AuditResult.SUCCESS,
                resourceType = RESOURCE_TYPE,
                resourceId = slotId.toString(),
                beforeValue = before,
                afterValue = after,
                approvalId = approved,
            )
            log.info("편성을 취소했습니다. 편성={} 승인={}", slotId, approved)
            after
        }

    private fun visibleChannels(user: LoginUser, channelCode: String?): List<Channel> {
        val requested = channelCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (requested != null) {
            SecurityFrameworkUtils.requireChannel(user, requested)
            return listOfNotNull(channels.findByCode(requested))
        }
        if (user.scopes.isEmpty()) return emptyList()
        return channels.findByCodeIn(user.scopes)
    }

    private fun candidate(contentId: Long, rating: String?, startAt: OffsetDateTime, endAt: OffsetDateTime): SlotCandidate {
        if (contentId <= 0) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "작품 번호 contentId 는 1 이상이어야 합니다.")
        }
        val parsedRating = rating?.takeIf { it.isNotBlank() }?.let {
            ContentRating.fromCode(it)
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "관람 등급은 ${ContentRating.codes()} 중 하나여야 합니다.")
        }
        val start = SeoulTime.toLocal(startAt)
        val end = SeoulTime.toLocal(endAt)
        if (!end.isAfter(start)) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "끝나는 시각은 시작 시각보다 늦어야 합니다.")
        }
        if (Duration.between(start, end) > MAX_SLOT_LENGTH) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "편성 한 건은 24시간을 넘을 수 없습니다.")
        }
        return SlotCandidate(contentId, parsedRating, start, end)
    }

    private fun findConflicts(channel: Channel, candidate: SlotCandidate, excludeSlotId: Long?): List<ScheduleConflict> {
        val others = slots.findOverlapping(channel.requireId(), candidate.startAt, candidate.endAt, SlotStatus.CANCELED)
            .filter { it.id != excludeSlotId }
            .map { it.window() }
        return ScheduleRules.check(candidate, channel.nightOnlyFrom(), others, exposures.isHidden(candidate.contentId))
    }

    private fun rejectConflicts(conflicts: List<ScheduleConflict>) {
        if (conflicts.isEmpty()) return
        throw BusinessException(ErrorCode.CONFLICT, conflicts.joinToString(" ") { it.message })
    }

    private fun requireApprovedExecution(user: LoginUser, approvalId: Long?, message: String): Long {
        if (approvalId == null || approvalId <= 0) {
            throw BusinessException(ErrorCode.APPROVAL_REQUIRED, "$message 승인 번호 헤더 X-Approval-Id 가 필요합니다.")
        }
        if (!user.service) {
            throw BusinessException(ErrorCode.APPROVAL_REQUIRED, "$message 승인된 요청을 실행하는 서비스만 부를 수 있습니다.")
        }
        return approvalId
    }

    private fun lockChannel(channel: Channel): Channel =
        channels.findLockedById(channel.requireId()) ?: throw BusinessException(ErrorCode.NOT_FOUND, "채널을 찾을 수 없습니다.")

    private fun requireSlot(slotId: Long): ScheduleSlot =
        slots.findById(slotId).orElse(null) ?: throw BusinessException(ErrorCode.NOT_FOUND, "편성을 찾을 수 없습니다.")

    private fun requireChannelById(channelId: Long): Channel =
        channels.findById(channelId).orElse(null) ?: throw BusinessException(ErrorCode.NOT_FOUND, "채널을 찾을 수 없습니다.")

    private fun requireChannelByCode(channelCode: String): Channel =
        channels.findByCode(channelCode.trim().uppercase())
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "채널을 찾을 수 없습니다: $channelCode")

    companion object {
        private const val RESOURCE_TYPE = "schedule_slot"
        private val APPROVAL_WINDOW: Duration = Duration.ofHours(2)
        private val MAX_SLOT_LENGTH: Duration = Duration.ofHours(24)
    }
}
