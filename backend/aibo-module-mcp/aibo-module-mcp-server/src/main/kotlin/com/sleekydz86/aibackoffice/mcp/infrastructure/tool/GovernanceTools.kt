package com.sleekydz86.aibackoffice.mcp.infrastructure.tool

import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolList
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolOutput
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolSchemas
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.DownstreamClients
import org.springframework.stereotype.Component

object GovernanceValues {
    val ACTOR_TYPES = setOf("HUMAN", "AGENT", "SYSTEM")
    val RESULTS = setOf("SUCCESS", "DENIED", "PENDING", "FAILED")
    const val MAX_DAYS = 90L

    fun data(approval: ApprovalResponse): Map<String, Any?> = mapOf(
        "id" to approval.id,
        "toolName" to approval.toolName,
        "arguments" to approval.arguments,
        "riskLevel" to approval.riskLevel,
        "status" to approval.status,
        "requesterId" to approval.requesterId,
        "requesterType" to approval.requesterType,
        "scopeChannel" to approval.scopeChannel,
        "approverId" to approval.approverId,
        "expiresAt" to approval.expiresAt.toString(),
        "decidedAt" to approval.decidedAt?.toString(),
        "result" to approval.result,
        "createdAt" to approval.createdAt.toString(),
    )

    fun text(approval: ApprovalResponse): Map<String, Any?> =
        mapOf("id" to approval.id, "reason" to approval.reason, "decisionComment" to approval.decisionComment)
}

@Component
class ApprovalListPendingTool(private val clients: DownstreamClients) : BaseTool(
    "approval_list_pending",
    "승인 대기 목록",
    "승인을 기다리는 요청을 읽는다. 승인 권한이 없으면 자기 요청만 보인다. 승인과 반려는 사람만 화면에서 한다.",
    ToolSchemas.obj(ToolSchemas.limit()),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val list = ToolResponses.list(
            clients.governance(call.user).approvals("PENDING", false, 0, ToolList.limit(call.arguments.int("limit"))),
        )
        return ToolOutput(
            data = ToolResponses.summary(list) + ("items" to list.items.map(GovernanceValues::data)),
            untrusted = list.items.map(GovernanceValues::text),
            message = "승인 대기 ${list.total}건 중 ${list.items.size}건을 읽었습니다.",
        )
    }
}

@Component
class ApprovalGetTool(private val clients: DownstreamClients) : BaseTool(
    "approval_get",
    "승인 요청 상세",
    "승인 요청 하나의 상태와 실행 결과를 읽는다. 자기 요청이 아니면 승인 권한이 있어야 보인다.",
    ToolSchemas.obj("approvalId" to ToolSchemas.integer("승인 요청 번호", 1), required = listOf("approvalId")),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val approvalId = call.arguments.requireLong("approvalId")
        val approval = clients.governance(call.user).approval(approvalId)
        return ToolOutput(
            data = GovernanceValues.data(approval),
            untrusted = GovernanceValues.text(approval),
            message = "승인 요청 ${approvalId}번은 ${approval.status} 상태입니다.",
        )
    }
}

@Component
class AuditSearchTool(private val clients: DownstreamClients) : BaseTool(
    "audit_search",
    "감사 기록 찾기",
    "행위자, 작업, 대상, 결과, 추적 번호, 기간으로 감사 기록을 찾는다. 바뀌기 전후 값은 untrusted 칸에 담긴다.",
    ToolSchemas.obj(
        "actorType" to ToolSchemas.choice("행위자 유형", GovernanceValues.ACTOR_TYPES.sorted()),
        "actorId" to ToolSchemas.string("행위자 번호", 64),
        "action" to ToolSchemas.string("작업 이름. 예 mcp.tool.call", 100),
        "resourceType" to ToolSchemas.string("대상 유형", 50),
        "resourceId" to ToolSchemas.string("대상 번호", 100),
        "result" to ToolSchemas.choice("결과", GovernanceValues.RESULTS.sorted()),
        "traceId" to ToolSchemas.string("추적 번호", 64),
        "from" to ToolSchemas.dateTime("이 시각부터"),
        "to" to ToolSchemas.dateTime("이 시각까지"),
        ToolSchemas.limit(),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val list = ToolResponses.list(
            clients.governance(call.user).auditLogs(
                args.choice("actorType", GovernanceValues.ACTOR_TYPES),
                args.string("actorId"),
                args.string("action"),
                args.string("resourceType"),
                args.string("resourceId"),
                args.choice("result", GovernanceValues.RESULTS),
                args.string("traceId"),
                args.dateTime("from"),
                args.dateTime("to"),
                0,
                ToolList.limit(args.int("limit")),
            ),
        )
        val values = arrayOf("beforeValue", "afterValue", "reason")
        return ToolOutput(
            data = ToolResponses.summary(list) + ("items" to list.items.map { ToolResponses.without(it, *values) }),
            untrusted = list.items.map { mapOf("id" to it["id"]) + ToolResponses.pick(it, *values) },
            message = "감사 기록 ${list.total}건 중 ${list.items.size}건을 찾았습니다.",
        )
    }
}

@Component
class AutomationListCandidatesTool(private val clients: DownstreamClients) : BaseTool(
    "automation_list_candidates",
    "자동화 후보",
    "최근 며칠 동안 사람이 자주 반복한 작업을 자동화 후보로 읽는다.",
    ToolSchemas.obj(
        "days" to ToolSchemas.integer("최근 며칠. 기본 7", 1, GovernanceValues.MAX_DAYS),
        ToolSchemas.limit(),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val days = (args.int("days") ?: DEFAULT_DAYS).coerceIn(1, GovernanceValues.MAX_DAYS.toInt())
        val candidates = clients.governance(call.user).automationCandidates(days, ToolList.limit(args.int("limit")))
        return ToolResponses.listed(ToolResponses.list(candidates), "최근 ${days}일 자동화 후보 ${candidates.size}건을 읽었습니다.")
    }

    companion object {
        private const val DEFAULT_DAYS = 7
    }
}
