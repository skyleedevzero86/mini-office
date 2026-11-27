package com.sleekydz86.aibackoffice.operation.presentation

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.framework.web.core.list.ListResponses
import com.sleekydz86.aibackoffice.operation.application.ExposureHideCommand
import com.sleekydz86.aibackoffice.operation.application.ExposureRestoreCommand
import com.sleekydz86.aibackoffice.operation.application.ExposureService
import com.sleekydz86.aibackoffice.operation.application.ExposureView
import com.sleekydz86.aibackoffice.operation.application.SummaryService
import com.sleekydz86.aibackoffice.operation.application.SummaryView
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
class ExposureController(
    private val exposureService: ExposureService,
    private val summaryService: SummaryService,
) {

    @GetMapping("/exposures")
    @PreAuthorize("@ss.hasPermission('issue:read')")
    fun exposures(@RequestParam(required = false) exposed: Boolean?, query: ListQuery): ResponseEntity<List<ExposureView>> {
        val page = exposureService.exposures(SecurityFrameworkUtils.loginUser(), exposed, query)
        return ListResponses.of(page.items, page.total)
    }

    @GetMapping("/exposures/{contentId}")
    @PreAuthorize("@ss.hasPermission('issue:read')")
    fun exposure(@PathVariable contentId: Long): ExposureView =
        exposureService.exposure(SecurityFrameworkUtils.loginUser(), contentId)

    @PostMapping("/exposures/{contentId}/hide")
    @PreAuthorize("@ss.hasPermission('exposure:write')")
    fun hide(
        @PathVariable contentId: Long,
        @RequestBody command: ExposureHideCommand,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): ExposureView = exposureService.hide(SecurityFrameworkUtils.loginUser(), contentId, command, approvalId, idempotencyKey)

    @PostMapping("/exposures/{contentId}/restore")
    @PreAuthorize("@ss.hasPermission('exposure:write')")
    fun restore(
        @PathVariable contentId: Long,
        @RequestBody command: ExposureRestoreCommand,
        @RequestHeader(Headers.APPROVAL_ID, required = false) approvalId: Long?,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): ExposureView = exposureService.restore(SecurityFrameworkUtils.loginUser(), contentId, command, approvalId, idempotencyKey)

    @GetMapping("/summary")
    @PreAuthorize("@ss.hasAnyPermissions('schedule:read', 'issue:read')")
    fun summary(): SummaryView = summaryService.summary(SecurityFrameworkUtils.loginUser())
}
