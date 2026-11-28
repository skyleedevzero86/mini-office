package com.sleekydz86.aibackoffice.operation.api

import com.sleekydz86.aibackoffice.common.Headers
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/api/operation")
interface OperationApi {

    @GetExchange("/schedule-slots")
    fun slots(
        @RequestParam(required = false) channelCode: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam("_start") start: Int,
        @RequestParam("_end") end: Int,
    ): ResponseEntity<List<Map<String, Any?>>>

    @GetExchange("/schedule-slots/{slotId}")
    fun slot(@PathVariable slotId: Long): Map<String, Any?>

    @GetExchange("/schedule-slots/conflicts")
    fun conflicts(
        @RequestParam channelCode: String,
        @RequestParam contentId: Long,
        @RequestParam(required = false) contentRating: String?,
        @RequestParam startAt: String,
        @RequestParam endAt: String,
        @RequestParam(required = false) excludeSlotId: Long?,
    ): List<Map<String, Any?>>

    @PostExchange("/schedule-slots")
    fun createDraft(
        @RequestBody request: SlotDraftRequest,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @PostExchange("/schedule-slots/{slotId}/publish")
    fun publish(
        @PathVariable slotId: Long,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @PostExchange("/schedule-slots/{slotId}/cancel")
    fun cancel(
        @PathVariable slotId: Long,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @GetExchange("/issues")
    fun issues(
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) contentId: Long?,
        @RequestParam(required = false) q: String?,
        @RequestParam("_start") start: Int,
        @RequestParam("_end") end: Int,
    ): ResponseEntity<List<Map<String, Any?>>>

    @GetExchange("/issues/{issueId}")
    fun issue(@PathVariable issueId: Long): Map<String, Any?>

    @PostExchange("/issues")
    fun createIssue(
        @RequestBody request: IssueCreateRequest,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @PostExchange("/issues/{issueId}/assign")
    fun assignIssue(
        @PathVariable issueId: Long,
        @RequestBody request: IssueAssignRequest,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @PostExchange("/issues/{issueId}/comments")
    fun commentIssue(
        @PathVariable issueId: Long,
        @RequestBody request: IssueCommentRequest,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @PostExchange("/issues/{issueId}/resolve")
    fun resolveIssue(
        @PathVariable issueId: Long,
        @RequestBody request: IssueResolveRequest,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @GetExchange("/exposures/{contentId}")
    fun exposure(@PathVariable contentId: Long): Map<String, Any?>

    @PostExchange("/exposures/{contentId}/hide")
    fun hide(
        @PathVariable contentId: Long,
        @RequestBody request: ExposureHideRequest,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>

    @PostExchange("/exposures/{contentId}/restore")
    fun restore(
        @PathVariable contentId: Long,
        @RequestBody request: ExposureRestoreRequest,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): Map<String, Any?>
}

data class SlotDraftRequest(
    val channelCode: String,
    val contentId: Long,
    val episodeId: Long?,
    val contentRating: String?,
    val startAt: String,
    val endAt: String,
)

data class IssueCreateRequest(
    val type: String,
    val severity: String,
    val contentId: Long?,
    val title: String,
    val description: String,
)

data class IssueAssignRequest(val assigneeId: Long)

data class IssueCommentRequest(val body: String)

data class IssueResolveRequest(val resolution: String)

data class ExposureHideRequest(val issueId: Long?, val reason: String)

data class ExposureRestoreRequest(val reason: String)
