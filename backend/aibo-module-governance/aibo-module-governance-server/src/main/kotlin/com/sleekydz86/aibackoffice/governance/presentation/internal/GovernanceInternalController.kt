package com.sleekydz86.aibackoffice.governance.presentation.internal

import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.governance.api.ApprovalCreateRequest
import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
import com.sleekydz86.aibackoffice.governance.api.ToolPolicyResponse
import com.sleekydz86.aibackoffice.governance.application.ApprovalService
import com.sleekydz86.aibackoffice.governance.application.AuditLogService
import com.sleekydz86.aibackoffice.governance.application.ToolPolicyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/governance")
class GovernanceInternalController(
    private val approvalService: ApprovalService,
    private val toolPolicyService: ToolPolicyService,
    private val auditLogService: AuditLogService,
) {

    @PostMapping("/approvals")
    fun requestApproval(@RequestBody request: ApprovalCreateRequest): ApprovalResponse =
        approvalService.submitByService(SecurityFrameworkUtils.loginUser(), request)

    @GetMapping("/tool-policies")
    fun policies(): List<ToolPolicyResponse> = toolPolicyService.policies()

    @PostMapping("/audit-events")
    fun auditEvents(@RequestBody events: List<Map<String, Any?>>): ResponseEntity<Void> {
        auditLogService.ingest(events)
        return ResponseEntity.accepted().build()
    }
}
