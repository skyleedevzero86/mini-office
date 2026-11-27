package com.sleekydz86.aibackoffice.operation.presentation

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.framework.web.core.list.ListResponses
import com.sleekydz86.aibackoffice.framework.web.core.list.toPageable
import com.sleekydz86.aibackoffice.operation.application.ChannelView
import com.sleekydz86.aibackoffice.operation.application.ConflictQuery
import com.sleekydz86.aibackoffice.operation.application.ConflictView
import com.sleekydz86.aibackoffice.operation.application.ScheduleService
import com.sleekydz86.aibackoffice.operation.application.ScheduleSlotView
import com.sleekydz86.aibackoffice.operation.application.SlotDraftCommand
import com.sleekydz86.aibackoffice.operation.application.SlotFilter
import com.sleekydz86.aibackoffice.operation.domain.SlotStatus
import java.time.OffsetDateTime
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/operation")
class ScheduleController(private val scheduleService: ScheduleService) {

    @GetMapping("/channels")
    @PreAuthorize("@ss.hasPermission('schedule:read')")
    fun channels(): List<ChannelView> = scheduleService.channels(SecurityFrameworkUtils.loginUser())

    @GetMapping("/schedule-slots")
    @PreAuthorize("@ss.hasPermission('schedule:read')")
    fun slots(
        @RequestParam(required = false) channelCode: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime?,
        @RequestParam(required = false) status: SlotStatus?,
        query: ListQuery,
    ): ResponseEntity<List<ScheduleSlotView>> {
        val page = scheduleService.slots(
            SecurityFrameworkUtils.loginUser(),
            SlotFilter(channelCode, from, to, status),
            query.toPageable(SORTABLE, DEFAULT_SORT),
        )
        return ListResponses.of(page.items, page.total)
    }

    @GetMapping("/schedule-slots/conflicts")
    @PreAuthorize("@ss.hasPermission('schedule:read')")
    fun conflicts(
        @RequestParam channelCode: String,
        @RequestParam contentId: Long,
        @RequestParam(required = false) contentRating: String?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startAt: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endAt: OffsetDateTime,
        @RequestParam(required = false) excludeSlotId: Long?,
    ): List<ConflictView> = scheduleService.conflicts(
        SecurityFrameworkUtils.loginUser(),
        ConflictQuery(channelCode, contentId, contentRating, startAt, endAt, excludeSlotId),
    )

    @GetMapping("/schedule-slots/{slotId}")
    @PreAuthorize("@ss.hasPermission('schedule:read')")
    fun slot(@PathVariable slotId: Long): ScheduleSlotView =
        scheduleService.slot(SecurityFrameworkUtils.loginUser(), slotId)

    @PostMapping("/schedule-slots")
    @PreAuthorize("@ss.hasPermission('schedule:draft')")
    fun createDraft(
        @RequestBody command: SlotDraftCommand,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): ScheduleSlotView = scheduleService.createDraft(SecurityFrameworkUtils.loginUser(), command, approvalId, idempotencyKey)

    @PostMapping("/schedule-slots/{slotId}/publish")
    @PreAuthorize("@ss.hasPermission('schedule:publish')")
    fun publish(
        @PathVariable slotId: Long,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): ScheduleSlotView = scheduleService.publish(SecurityFrameworkUtils.loginUser(), slotId, approvalId, idempotencyKey)

    @PostMapping("/schedule-slots/{slotId}/cancel")
    @PreAuthorize("@ss.hasPermission('schedule:publish')")
    fun cancel(
        @PathVariable slotId: Long,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): ScheduleSlotView = scheduleService.cancel(SecurityFrameworkUtils.loginUser(), slotId, approvalId, idempotencyKey)

    companion object {
        private val SORTABLE = setOf("startAt", "endAt", "createdAt", "id")
        private val DEFAULT_SORT: Sort = Sort.by(Sort.Direction.ASC, "startAt")
    }
}
