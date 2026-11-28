package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.governance.api.ApprovalExecuteRequest
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolArguments
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.domain.ToolAccess
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

@Service
class ApprovedExecutionService(
    private val catalog: ToolCatalog,
    private val policies: ToolPolicySource,
    private val ledger: ExecutionLedger,
    private val auditor: ToolAuditor,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val failures = ToolFailures(jsonMapper)

    fun execute(user: LoginUser, approvalId: Long, request: ApprovalExecuteRequest): ToolResult {
        rejectCaller(user, approvalId, request)?.let { rejected ->
            auditor.record(user, ToolAuditor.EXECUTE_ACTION, request.toolName, request.arguments, rejected, approvalId)
            return rejected
        }
        val entry = try {
            ledger.begin(approvalId)
        } catch (exception: RuntimeException) {
            log.error("중복 실행을 막는 저장소에 닿지 못해 실행하지 않았습니다. 승인 요청={}", approvalId, exception)
            val failed = ToolResult.fail(ErrorCode.UPSTREAM_FAILED, "중복 실행을 막는 저장소에 닿지 못해 실행하지 않았습니다.")
            auditor.record(user, ToolAuditor.EXECUTE_ACTION, request.toolName, request.arguments, failed, approvalId)
            return failed
        }
        when (entry) {
            is LedgerEntry.Done -> {
                log.info("이미 실행한 승인 요청이라 처음 결과를 돌려줍니다. 승인 요청={}", approvalId)
                return jsonMapper.readValue(entry.resultJson, ToolResult::class.java)
            }
            LedgerEntry.Running -> {
                log.warn("같은 승인 요청이 이미 실행 중입니다. 승인 요청={}", approvalId)
                return ToolResult.fail(ErrorCode.INVALID_STATE, "같은 승인 요청이 이미 실행 중입니다.")
            }
            LedgerEntry.Started -> Unit
        }
        val result = try {
            run(user, approvalId, request)
        } catch (error: Exception) {
            failures.toResult(error, request.toolName)
        }
        try {
            ledger.finish(approvalId, jsonMapper.writeValueAsString(result))
        } catch (exception: RuntimeException) {
            log.error("승인 실행 결과를 저장하지 못했습니다. 승인 요청={}", approvalId, exception)
        }
        auditor.record(user, ToolAuditor.EXECUTE_ACTION, request.toolName, request.arguments, result, approvalId)
        log.info("승인한 툴을 실행했습니다. 승인 요청={} 툴={} 요청자={} 결과={}", approvalId, request.toolName, user.onBehalfOf, result.code)
        return result
    }

    private fun rejectCaller(user: LoginUser, approvalId: Long, request: ApprovalExecuteRequest): ToolResult? {
        if (!user.service || user.onBehalfOf.isNullOrBlank()) {
            return ToolResult.fail(ErrorCode.PERMISSION_DENIED, "요청자 대신 발급한 서비스 토큰으로만 실행할 수 있습니다.")
        }
        if (request.approvalId != approvalId) {
            return ToolResult.fail(ErrorCode.VALIDATION_FAILED, "주소의 승인 번호와 본문의 승인 번호가 다릅니다.")
        }
        if (request.idempotencyKey != idempotencyKey(approvalId)) {
            return ToolResult.fail(ErrorCode.VALIDATION_FAILED, "멱등 키는 승인 번호로 만든 ${idempotencyKey(approvalId)} 이어야 합니다.")
        }
        return null
    }

    private fun run(user: LoginUser, approvalId: Long, request: ApprovalExecuteRequest): ToolResult {
        val handler = catalog.find(request.toolName)
            ?: return ToolResult.fail(ErrorCode.NOT_FOUND, "없는 툴입니다: ${request.toolName}")
        val policy = policies.find(request.toolName)
            ?: return ToolResult.fail(ErrorCode.NOT_FOUND, "툴 정책이 없습니다: ${request.toolName}")
        if (!policy.enabled) return ToolResult.fail(ErrorCode.TOOL_DISABLED, "사용이 꺼진 툴입니다: ${request.toolName}")
        if (!policy.needsApproval) {
            return ToolResult.fail(ErrorCode.INVALID_STATE, "승인을 거쳐 실행하는 툴이 아닙니다: ${request.toolName}")
        }
        if (!ToolAccess.permitted(user, policy.requiredPermission)) {
            return ToolResult.fail(ErrorCode.PERMISSION_DENIED, "요청자에게 지금은 ${policy.requiredPermission} 권한이 없습니다.")
        }
        val call = ToolCall(user, ToolArguments(request.arguments), approvalId, idempotencyKey(approvalId))
        val channel = handler.scopeChannel(call)
        if (channel != null && !user.inChannel(channel)) {
            return ToolResult.fail(ErrorCode.SCOPE_DENIED, "요청자가 지금은 $channel 채널을 맡고 있지 않습니다.")
        }
        handler.precheck(call)
        val output = handler.execute(call)
        return ToolResult.ok(output.data, output.untrusted, output.message).copy(approvalId = approvalId)
    }

    companion object {
        fun idempotencyKey(approvalId: Long): String = "approval:$approvalId"
    }
}
