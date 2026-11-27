package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.governance.api.ToolPolicyResponse
import com.sleekydz86.aibackoffice.governance.infrastructure.persistence.ToolPolicyStore
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ToolPolicyService(
    private val policies: ToolPolicyStore,
    private val views: GovernanceViews,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun policies(): List<ToolPolicyResponse> = policies.findAll().map(views::policy)

    @Transactional
    fun update(user: LoginUser, toolName: String, command: ToolPolicyUpdateCommand): ToolPolicyResponse {
        SecurityFrameworkUtils.requirePermission(user, Permissions.POLICY_WRITE)
        val current = policies.findForUpdate(toolName)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "툴 정책을 찾을 수 없습니다: $toolName")
        val riskLevel = command.riskLevel?.let { value ->
            RiskLevel.entries.firstOrNull { it.name == value.trim().uppercase() }
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "위험도는 ${RiskLevel.entries.joinToString(", ")} 중 하나여야 합니다.")
        } ?: current.riskLevel
        val permission = command.requiredPermission?.trim()?.let {
            if (it !in ALLOWED_PERMISSIONS) throw BusinessException(ErrorCode.VALIDATION_FAILED, "없는 권한 코드입니다: $it")
            it
        } ?: current.requiredPermission
        val ttl = command.approvalTtlMinutes ?: current.approvalTtlMinutes
        if (ttl !in 0..MAX_TTL_MINUTES) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "승인 대기 시간은 0분부터 ${MAX_TTL_MINUTES}분 사이여야 합니다.")
        }
        val next = current.copy(
            riskLevel = riskLevel,
            requiredPermission = permission,
            enabled = command.enabled ?: current.enabled,
            approvalTtlMinutes = ttl,
            updatedAt = LocalDateTime.now(clock),
        )
        policies.update(next)
        val before = views.policy(current)
        val after = views.policy(next)
        audit.record(
            user,
            action = "tool_policy.update",
            result = AuditResult.SUCCESS,
            resourceType = "tool_policy",
            resourceId = toolName,
            beforeValue = before,
            afterValue = after,
        )
        log.info("툴 정책을 바꿨습니다. 툴={} 위험도={} 사용={}", toolName, next.riskLevel, next.enabled)
        return after
    }

    companion object {
        private const val MAX_TTL_MINUTES = 10_080
        private val ALLOWED_PERMISSIONS = Permissions.ALL.toSet() + Permissions.APPROVAL_READ_SELF
    }
}
