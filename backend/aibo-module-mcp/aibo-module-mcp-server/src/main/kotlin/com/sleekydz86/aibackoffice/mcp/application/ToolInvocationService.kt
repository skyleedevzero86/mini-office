package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.mcp.application.tool.McpToolHandler
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolArguments
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.domain.AccessDecision
import com.sleekydz86.aibackoffice.mcp.domain.ToolAccess
import com.sleekydz86.aibackoffice.mcp.domain.ToolPolicy
import java.security.MessageDigest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

@Service
class ToolInvocationService(
    private val catalog: ToolCatalog,
    private val policies: ToolPolicySource,
    private val approvals: ApprovalGateway,
    private val auditor: ToolAuditor,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val failures = ToolFailures(jsonMapper)

    fun call(user: LoginUser, toolName: String, arguments: Map<String, Any?>, meta: CallMeta): ToolResult {
        val result = try {
            run(user, toolName, arguments, meta)
        } catch (error: Exception) {
            failures.toResult(error, toolName)
        }
        auditor.record(user, ToolAuditor.CALL_ACTION, toolName, arguments, result, null)
        log.info("툴 호출을 처리했습니다. 툴={} 사용자={} 행위자={} 결과={}", toolName, user.effectiveUserId, user.actorType, result.code)
        return result
    }

    private fun run(user: LoginUser, toolName: String, arguments: Map<String, Any?>, meta: CallMeta): ToolResult {
        if (user.service) {
            return ToolResult.fail(ErrorCode.PERMISSION_DENIED, "서비스 토큰으로는 툴을 부를 수 없습니다. 사용자 토큰이 필요합니다.")
        }
        val handler = catalog.find(toolName)
        val policy = policies.find(toolName)
        val decision = ToolAccess.decide(user, toolName, handler != null, policy)
        if (decision is AccessDecision.Denied) return ToolResult.fail(decision.code, decision.message)
        if (handler == null || policy == null) return ToolResult.fail(ErrorCode.NOT_FOUND, "없는 툴입니다: $toolName")
        val call = ToolCall(user, ToolArguments(arguments), null, meta.idempotencyKey)
        val channel = handler.scopeChannel(call)
        if (channel != null && !user.inChannel(channel)) {
            return ToolResult.fail(ErrorCode.SCOPE_DENIED, "담당하는 채널 밖의 대상입니다: $channel")
        }
        if (policy.needsApproval) return requestApproval(user, handler, policy, call, channel, meta)
        handler.precheck(call)
        val output = handler.execute(call)
        return ToolResult.ok(output.data, output.untrusted, output.message)
    }

    private fun requestApproval(
        user: LoginUser,
        handler: McpToolHandler,
        policy: ToolPolicy,
        call: ToolCall,
        channel: String?,
        meta: CallMeta,
    ): ToolResult {
        handler.precheck(call)
        val args = call.arguments
        val approval = approvals.request(
            user,
            ApprovalDraft(
                toolName = policy.toolName,
                arguments = args.values,
                reason = args.string(REASON_ARGUMENT),
                scopeChannel = channel,
                conversationId = meta.conversationId,
                idempotencyKey = approvalKey(policy.toolName, args.values, meta),
            ),
        )
        if (approval.status != PENDING) {
            throw BusinessException(ErrorCode.INVALID_STATE, "같은 요청이 이미 처리되었습니다. 승인 요청 ${approval.id}번 상태는 ${approval.status} 입니다.")
        }
        return ToolResult.pending(approval)
    }

    private fun approvalKey(toolName: String, arguments: Map<String, Any?>, meta: CallMeta): String? {
        meta.idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(MAX_KEY) }
        val conversation = meta.conversationId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val source = jsonMapper.writeValueAsString(mapOf("c" to conversation, "t" to toolName, "a" to arguments.toSortedMap()))
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return "mcp:" + digest.joinToString("") { "%02x".format(it) }.take(MAX_KEY - 4)
    }

    companion object {
        const val REASON_ARGUMENT = "reason"
        private const val PENDING = "PENDING"
        private const val MAX_KEY = 64
    }
}
