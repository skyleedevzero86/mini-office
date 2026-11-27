package com.sleekydz86.aibackoffice.governance.api

import java.time.OffsetDateTime
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/internal/governance")
interface GovernanceInternalApi {

    @PostExchange("/approvals")
    fun requestApproval(@RequestBody request: ApprovalCreateRequest): ApprovalResponse

    @GetExchange("/tool-policies")
    fun toolPolicies(): List<ToolPolicyResponse>
}

data class ApprovalCreateRequest(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val reason: String?,
    val requesterType: String = "AGENT",
    val requesterName: String? = null,
    val conversationId: String? = null,
    val scopeChannel: String? = null,
    val idempotencyKey: String? = null,
)

data class ApprovalResponse(
    val id: Long,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val riskLevel: String,
    val requesterId: String,
    val requesterName: String?,
    val requesterType: String,
    val conversationId: String?,
    val reason: String?,
    val scopeChannel: String?,
    val status: String,
    val approverId: String?,
    val decisionComment: String?,
    val decidedAt: OffsetDateTime?,
    val expiresAt: OffsetDateTime,
    val result: Map<String, Any?>?,
    val traceId: String?,
    val createdAt: OffsetDateTime,
)

data class ToolPolicyResponse(
    val toolName: String,
    val domain: String,
    val riskLevel: String,
    val requiredPermission: String,
    val enabled: Boolean,
    val approvalTtlMinutes: Int,
    val updatedAt: OffsetDateTime,
)

data class ApprovalExecuteRequest(
    val approvalId: Long,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val idempotencyKey: String,
)
