package com.sleekydz86.aibackoffice.governance.presentation

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.framework.web.core.list.ListResponses
import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
import com.sleekydz86.aibackoffice.governance.api.ToolPolicyResponse
import com.sleekydz86.aibackoffice.governance.application.ApprovalDecisionCommand
import com.sleekydz86.aibackoffice.governance.application.ApprovalFilter
import com.sleekydz86.aibackoffice.governance.application.ApprovalService
import com.sleekydz86.aibackoffice.governance.application.ApprovalSubmitCommand
import com.sleekydz86.aibackoffice.governance.application.ToolPolicyService
import com.sleekydz86.aibackoffice.governance.application.ToolPolicyUpdateCommand
import com.sleekydz86.aibackoffice.governance.domain.ApprovalStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/governance")
class GovernanceController(
    private val toolPolicyService: ToolPolicyService,
    private val approvalService: ApprovalService,
) {

    @GetMapping("/tool-policies")
    fun policies(): List<ToolPolicyResponse> = toolPolicyService.policies()

    @PutMapping("/tool-policies/{toolName}")
    @PreAuthorize("@ss.hasPermission('policy:write')")
    fun updatePolicy(@PathVariable toolName: String, @RequestBody command: ToolPolicyUpdateCommand): ToolPolicyResponse =
        toolPolicyService.update(SecurityFrameworkUtils.loginUser(), toolName, command)

    @GetMapping("/approvals")
    fun approvals(
        @RequestParam(required = false) status: ApprovalStatus?,
        @RequestParam(required = false, defaultValue = "false") mine: Boolean,
        query: ListQuery,
    ): ResponseEntity<List<ApprovalResponse>> {
        val page = approvalService.list(SecurityFrameworkUtils.loginUser(), ApprovalFilter(status, mine), query)
        return ListResponses.of(page.items, page.total)
    }

    @GetMapping("/approvals/{approvalId}")
    fun approval(@PathVariable approvalId: Long): ApprovalResponse =
        approvalService.get(SecurityFrameworkUtils.loginUser(), approvalId)

    @PostMapping("/approvals")
    fun submit(
        @RequestBody command: ApprovalSubmitCommand,
        @RequestHeader(Headers.IDEMPOTENCY_KEY, required = false) idempotencyKey: String?,
    ): ApprovalResponse = approvalService.submitByHuman(SecurityFrameworkUtils.loginUser(), command, idempotencyKey)

    @PostMapping("/approvals/{approvalId}/approve")
    @PreAuthorize("@ss.hasPermission('approval:decide')")
    fun approve(@PathVariable approvalId: Long, @RequestBody(required = false) command: ApprovalDecisionCommand?): ApprovalResponse =
        approvalService.approve(SecurityFrameworkUtils.loginUser(), approvalId, command ?: ApprovalDecisionCommand())

    @PostMapping("/approvals/{approvalId}/reject")
    @PreAuthorize("@ss.hasPermission('approval:decide')")
    fun reject(@PathVariable approvalId: Long, @RequestBody command: ApprovalDecisionCommand): ApprovalResponse =
        approvalService.reject(SecurityFrameworkUtils.loginUser(), approvalId, command)
}
