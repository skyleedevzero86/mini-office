package com.sleekydz86.aibackoffice.governance.domain

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.RiskLevel
import java.time.LocalDateTime

enum class ApprovalStatus { PENDING, APPROVED, REJECTED, EXPIRED, EXECUTED, FAILED }

data class ToolPolicy(
    val toolName: String,
    val domain: String,
    val riskLevel: RiskLevel,
    val requiredPermission: String,
    val enabled: Boolean,
    val approvalTtlMinutes: Int,
    val updatedAt: LocalDateTime,
) {
    val needsApproval: Boolean get() = riskLevel == RiskLevel.WRITE_HIGH
}

data class ApprovalRequest(
    val id: Long?,
    val toolName: String,
    val argumentsJson: String,
    val riskLevel: RiskLevel,
    val requesterId: String,
    val requesterName: String?,
    val requesterType: ActorType,
    val conversationId: String?,
    val scopeChannel: String?,
    val reason: String?,
    val status: ApprovalStatus,
    val approverId: String?,
    val decisionComment: String?,
    val decidedAt: LocalDateTime?,
    val expiresAt: LocalDateTime,
    val idempotencyKey: String,
    val resultJson: String?,
    val traceId: String?,
    val createdAt: LocalDateTime,
) {
    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 승인 요청입니다.")

    fun expiredAt(now: LocalDateTime): Boolean = status == ApprovalStatus.PENDING && !now.isBefore(expiresAt)
}
