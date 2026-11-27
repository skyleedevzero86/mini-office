package com.sleekydz86.aibackoffice.operation.presentation

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.framework.web.core.list.ListResponses
import com.sleekydz86.aibackoffice.framework.web.core.list.toPageable
import com.sleekydz86.aibackoffice.operation.application.CommentView
import com.sleekydz86.aibackoffice.operation.application.IssueAssignCommand
import com.sleekydz86.aibackoffice.operation.application.IssueCommentCommand
import com.sleekydz86.aibackoffice.operation.application.IssueCreateCommand
import com.sleekydz86.aibackoffice.operation.application.IssueDetailView
import com.sleekydz86.aibackoffice.operation.application.IssueFilter
import com.sleekydz86.aibackoffice.operation.application.IssueResolveCommand
import com.sleekydz86.aibackoffice.operation.application.IssueService
import com.sleekydz86.aibackoffice.operation.application.IssueView
import com.sleekydz86.aibackoffice.operation.domain.IssueStatus
import com.sleekydz86.aibackoffice.operation.domain.IssueType
import com.sleekydz86.aibackoffice.operation.domain.Severity
import org.springframework.data.domain.Sort
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
@RequestMapping("/api/operation/issues")
class IssueController(private val issueService: IssueService) {

    @GetMapping
    @PreAuthorize("@ss.hasPermission('issue:read')")
    fun issues(
        @RequestParam(required = false) type: IssueType?,
        @RequestParam(required = false) severity: Severity?,
        @RequestParam(required = false) status: IssueStatus?,
        @RequestParam(required = false) contentId: Long?,
        @RequestParam(required = false) q: String?,
        query: ListQuery,
    ): ResponseEntity<List<IssueView>> {
        val page = issueService.issues(
            SecurityFrameworkUtils.loginUser(),
            IssueFilter(type, severity, status, contentId, q),
            query.toPageable(SORTABLE, DEFAULT_SORT),
        )
        return ListResponses.of(page.items, page.total)
    }

    @GetMapping("/{issueId}")
    @PreAuthorize("@ss.hasPermission('issue:read')")
    fun issue(@PathVariable issueId: Long): IssueDetailView = issueService.issue(SecurityFrameworkUtils.loginUser(), issueId)

    @PostMapping
    @PreAuthorize("@ss.hasPermission('issue:write')")
    fun create(
        @RequestBody command: IssueCreateCommand,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): IssueView = issueService.create(SecurityFrameworkUtils.loginUser(), command, idempotencyKey)

    @PostMapping("/{issueId}/assign")
    @PreAuthorize("@ss.hasPermission('issue:write')")
    fun assign(
        @PathVariable issueId: Long,
        @RequestBody command: IssueAssignCommand,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): IssueView = issueService.assign(SecurityFrameworkUtils.loginUser(), issueId, command, idempotencyKey)

    @PostMapping("/{issueId}/comments")
    @PreAuthorize("@ss.hasPermission('issue:write')")
    fun comment(
        @PathVariable issueId: Long,
        @RequestBody command: IssueCommentCommand,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): CommentView = issueService.comment(SecurityFrameworkUtils.loginUser(), issueId, command, idempotencyKey)

    @PostMapping("/{issueId}/resolve")
    @PreAuthorize("@ss.hasPermission('issue:write')")
    fun resolve(
        @PathVariable issueId: Long,
        @RequestBody command: IssueResolveCommand,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): IssueView = issueService.resolve(SecurityFrameworkUtils.loginUser(), issueId, command, idempotencyKey)

    companion object {
        private val SORTABLE = setOf("createdAt", "severity", "status", "id")
        private val DEFAULT_SORT: Sort = Sort.by(Sort.Direction.DESC, "createdAt")
    }
}
