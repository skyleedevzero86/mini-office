package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
import com.sleekydz86.aibackoffice.governance.api.ToolPolicyResponse
import com.sleekydz86.aibackoffice.governance.domain.ApprovalRequest
import com.sleekydz86.aibackoffice.governance.domain.ApprovalStatus
import com.sleekydz86.aibackoffice.governance.domain.ToolPolicy
import java.time.LocalDateTime
import java.time.OffsetDateTime
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

data class ToolPolicyUpdateCommand(
    val riskLevel: String? = null,
    val requiredPermission: String? = null,
    val enabled: Boolean? = null,
    val approvalTtlMinutes: Int? = null,
)

data class ApprovalSubmitCommand(
    val toolName: String? = null,
    val arguments: Map<String, Any?>? = null,
    val reason: String? = null,
    val conversationId: String? = null,
)

data class ApprovalDecisionCommand(val comment: String? = null)

data class ApprovalFilter(val status: ApprovalStatus? = null, val mine: Boolean = false)

data class ApprovalPage(val items: List<ApprovalResponse>, val total: Long)

object SeoulTime {
    fun toOffset(value: LocalDateTime): OffsetDateTime = value.atZone(Zones.SEOUL).toOffsetDateTime()
}

class GovernanceViews(private val jsonMapper: JsonMapper) {

    fun policy(policy: ToolPolicy): ToolPolicyResponse = ToolPolicyResponse(
        toolName = policy.toolName,
        domain = policy.domain,
        riskLevel = policy.riskLevel.name,
        requiredPermission = policy.requiredPermission,
        enabled = policy.enabled,
        approvalTtlMinutes = policy.approvalTtlMinutes,
        updatedAt = SeoulTime.toOffset(policy.updatedAt),
    )

    fun approval(request: ApprovalRequest): ApprovalResponse = ApprovalResponse(
        id = request.requireId(),
        toolName = request.toolName,
        arguments = readMap(request.argumentsJson) ?: emptyMap(),
        riskLevel = request.riskLevel.name,
        requesterId = request.requesterId,
        requesterName = request.requesterName,
        requesterType = request.requesterType.name,
        conversationId = request.conversationId,
        reason = request.reason,
        scopeChannel = request.scopeChannel,
        status = request.status.name,
        approverId = request.approverId,
        decisionComment = request.decisionComment,
        decidedAt = request.decidedAt?.let(SeoulTime::toOffset),
        expiresAt = SeoulTime.toOffset(request.expiresAt),
        result = readMap(request.resultJson),
        traceId = request.traceId,
        createdAt = SeoulTime.toOffset(request.createdAt),
    )

    fun write(value: Any?): String = jsonMapper.writeValueAsString(value)

    fun readMap(json: String?): Map<String, Any?>? =
        json?.let { jsonMapper.readValue(it, object : TypeReference<Map<String, Any?>>() {}) }
}
