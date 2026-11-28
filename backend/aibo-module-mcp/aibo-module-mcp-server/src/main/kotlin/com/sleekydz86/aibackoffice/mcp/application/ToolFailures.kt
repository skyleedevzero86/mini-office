package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

class ToolFailures(private val jsonMapper: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun toResult(error: Throwable, toolName: String): ToolResult = when (error) {
        is BusinessException -> ToolResult.fail(error.code, error.message ?: error.code.defaultMessage)
        is RestClientResponseException -> fromResponse(error, toolName)
        is ResourceAccessException -> {
            log.warn("툴이 부른 서비스에 닿지 못했습니다. 툴={}", toolName)
            ToolResult.fail(ErrorCode.UPSTREAM_FAILED, "툴이 부른 서비스에 닿지 못했습니다. 잠시 뒤 다시 시도하세요.")
        }
        else -> {
            log.error("툴을 실행하다 예상하지 못한 오류가 났습니다. 툴={}", toolName, error)
            ToolResult.fail(ErrorCode.INTERNAL_ERROR, "툴을 실행하다 오류가 났습니다.")
        }
    }

    private fun fromResponse(error: RestClientResponseException, toolName: String): ToolResult {
        val body = runCatching { jsonMapper.readValue(error.responseBodyAsString, Map::class.java) }.getOrNull()
        val code = (body?.get("code") as? String)?.takeIf { value -> ErrorCode.entries.any { it.name == value } }
        val message = body?.get("message") as? String
        if (code != null && message != null) return ToolResult.fail(code, message)
        log.warn("툴이 부른 서비스가 오류를 돌려주었습니다. 툴={} 상태={}", toolName, error.statusCode.value())
        val fallback = when (error.statusCode.value()) {
            401 -> ErrorCode.UNAUTHORIZED
            403 -> ErrorCode.PERMISSION_DENIED
            404 -> ErrorCode.NOT_FOUND
            409 -> ErrorCode.CONFLICT
            in 400..499 -> ErrorCode.VALIDATION_FAILED
            else -> ErrorCode.UPSTREAM_FAILED
        }
        return ToolResult.fail(fallback)
    }

    companion object {
        private val DENIED_CODES = setOf(
            ErrorCode.PERMISSION_DENIED.name,
            ErrorCode.SCOPE_DENIED.name,
            ErrorCode.TOOL_DISABLED.name,
            ErrorCode.APPROVAL_REQUIRED.name,
            ErrorCode.UNAUTHORIZED.name,
        )

        fun auditResult(result: ToolResult): AuditResult = when {
            result.ok -> AuditResult.SUCCESS
            result.code == ErrorCode.PENDING_APPROVAL.name -> AuditResult.PENDING
            result.code in DENIED_CODES -> AuditResult.DENIED
            else -> AuditResult.FAILED
        }
    }
}
