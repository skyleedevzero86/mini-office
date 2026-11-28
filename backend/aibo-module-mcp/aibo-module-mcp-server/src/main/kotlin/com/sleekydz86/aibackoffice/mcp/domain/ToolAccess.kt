package com.sleekydz86.aibackoffice.mcp.domain

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser

data class ToolPolicy(
    val toolName: String,
    val domain: String,
    val riskLevel: RiskLevel,
    val requiredPermission: String,
    val enabled: Boolean,
    val approvalTtlMinutes: Int,
) {
    val needsApproval: Boolean get() = riskLevel == RiskLevel.WRITE_HIGH
}

sealed interface AccessDecision {
    data object Allowed : AccessDecision

    data class Denied(val code: ErrorCode, val message: String) : AccessDecision
}

object ToolAccess {

    val NEVER_EXPOSED: Set<String> = setOf("approval_approve", "approval_reject", "approval_decide")

    fun decide(user: LoginUser, toolName: String, registered: Boolean, policy: ToolPolicy?): AccessDecision {
        if (toolName in NEVER_EXPOSED) {
            return AccessDecision.Denied(ErrorCode.PERMISSION_DENIED, "승인과 반려는 사람만 화면에서 할 수 있습니다.")
        }
        if (!registered || policy == null) {
            return AccessDecision.Denied(ErrorCode.NOT_FOUND, "없는 툴입니다: $toolName")
        }
        if (!policy.enabled) {
            return AccessDecision.Denied(ErrorCode.TOOL_DISABLED, "사용이 꺼진 툴입니다: $toolName")
        }
        if (!permitted(user, policy.requiredPermission)) {
            return AccessDecision.Denied(ErrorCode.PERMISSION_DENIED, "이 툴에는 ${policy.requiredPermission} 권한이 필요합니다.")
        }
        return AccessDecision.Allowed
    }

    fun permitted(user: LoginUser, requiredPermission: String): Boolean =
        requiredPermission == Permissions.APPROVAL_READ_SELF || user.has(requiredPermission)
}
