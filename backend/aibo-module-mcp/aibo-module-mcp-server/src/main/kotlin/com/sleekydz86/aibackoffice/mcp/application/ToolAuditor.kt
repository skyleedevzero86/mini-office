package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ToolAuditor(private val audit: AuditRecorder) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(user: LoginUser, action: String, toolName: String, arguments: Map<String, Any?>, result: ToolResult, approvalId: Long?) {
        try {
            audit.record(
                user,
                action = action,
                result = ToolFailures.auditResult(result),
                resourceType = RESOURCE_TYPE,
                resourceId = toolName,
                afterValue = mapOf("arguments" to arguments, "code" to result.code),
                reason = if (result.ok) null else result.message,
                approvalId = approvalId ?: result.approvalId,
            )
        } catch (exception: RuntimeException) {
            log.error("툴 호출 감사 기록을 남기지 못했습니다. 툴={} 결과={}", toolName, result.code, exception)
        }
    }

    companion object {
        const val RESOURCE_TYPE = "mcp_tool"
        const val CALL_ACTION = "mcp.tool.call"
        const val EXECUTE_ACTION = "mcp.tool.execute_approved"
    }
}
