package com.sleekydz86.aibackoffice.mcp.infrastructure.tool

import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolList
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolOutput
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolSchemas
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.DownstreamClients
import com.sleekydz86.aibackoffice.operation.api.ExposureHideRequest
import com.sleekydz86.aibackoffice.operation.api.ExposureRestoreRequest
import com.sleekydz86.aibackoffice.operation.api.IssueAssignRequest
import com.sleekydz86.aibackoffice.operation.api.IssueCommentRequest
import com.sleekydz86.aibackoffice.operation.api.IssueCreateRequest
import com.sleekydz86.aibackoffice.operation.api.IssueResolveRequest
import org.springframework.stereotype.Component

object IssueValues {
    val TYPES = setOf("PLAYBACK_ERROR", "META_ERROR", "RIGHTS")
    val SEVERITIES = setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
    val STATUSES = setOf("OPEN", "ASSIGNED", "RESOLVED")
    const val MAX_TITLE = 200
    const val MAX_TEXT = 4000
    val TEXT_FIELDS = arrayOf("title", "description", "resolution", "comments", "reporter")

    fun split(issue: Map<String, Any?>): Pair<Map<String, Any?>, Map<String, Any?>> =
        ToolResponses.without(issue, *TEXT_FIELDS) to (mapOf("id" to issue["id"]) + ToolResponses.pick(issue, *TEXT_FIELDS))
}

@Component
class IssueSearchTool(private val clients: DownstreamClients) : BaseTool(
    "issue_search",
    "이슈 찾기",
    "유형, 심각도, 상태, 작품 번호, 검색어로 이슈를 찾는다. 제목과 본문은 사람이 쓴 글이라 untrusted 칸에 담긴다.",
    ToolSchemas.obj(
        "type" to ToolSchemas.choice("이슈 유형", IssueValues.TYPES.sorted()),
        "severity" to ToolSchemas.choice("심각도", IssueValues.SEVERITIES.sorted()),
        "status" to ToolSchemas.choice("상태", IssueValues.STATUSES.sorted()),
        "contentId" to ToolSchemas.integer("작품 번호", 1),
        "q" to ToolSchemas.string("제목 검색어", 100),
        ToolSchemas.limit(),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val list = ToolResponses.list(
            clients.operation(call.user).issues(
                args.choice("type", IssueValues.TYPES),
                args.choice("severity", IssueValues.SEVERITIES),
                args.choice("status", IssueValues.STATUSES),
                args.long("contentId"),
                args.string("q"),
                0,
                ToolList.limit(args.int("limit")),
            ),
        )
        val parts = list.items.map(IssueValues::split)
        return ToolOutput(
            data = ToolResponses.summary(list) + ("items" to parts.map { it.first }),
            untrusted = parts.map { it.second },
            message = "이슈 ${list.total}건 중 ${list.items.size}건을 찾았습니다.",
        )
    }
}

@Component
class IssueGetTool(private val clients: DownstreamClients) : BaseTool(
    "issue_get",
    "이슈 상세",
    "이슈 하나와 댓글을 읽는다. 제목, 본문, 댓글은 untrusted 칸에 담긴다.",
    ToolSchemas.obj("issueId" to ToolSchemas.integer("이슈 번호", 1), required = listOf("issueId")),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val issueId = call.arguments.requireLong("issueId")
        val (data, text) = IssueValues.split(clients.operation(call.user).issue(issueId))
        return ToolOutput(data = data, untrusted = text, message = "이슈 ${issueId}번을 읽었습니다.")
    }
}

@Component
class IssueCreateTool(private val clients: DownstreamClients) : BaseTool(
    "issue_create",
    "이슈 등록",
    "재생 오류, 메타 오류, 권리 문제 이슈를 등록한다.",
    ToolSchemas.obj(
        "type" to ToolSchemas.choice("이슈 유형", IssueValues.TYPES.sorted()),
        "severity" to ToolSchemas.choice("심각도", IssueValues.SEVERITIES.sorted()),
        "contentId" to ToolSchemas.integer("작품 번호", 1),
        "title" to ToolSchemas.string("제목", IssueValues.MAX_TITLE),
        "description" to ToolSchemas.string("본문", IssueValues.MAX_TEXT),
        required = listOf("type", "severity", "title", "description"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val issue = clients.operation(call.user).createIssue(
            IssueCreateRequest(
                type = args.requireChoice("type", IssueValues.TYPES),
                severity = args.requireChoice("severity", IssueValues.SEVERITIES),
                contentId = args.long("contentId"),
                title = args.requireText("title", IssueValues.MAX_TITLE),
                description = args.requireText("description", IssueValues.MAX_TEXT),
            ),
            call.idempotencyKey,
        )
        val (data, text) = IssueValues.split(issue)
        return ToolOutput(data = data, untrusted = text, message = "이슈 ${issue["id"]}번을 등록했습니다.")
    }
}

@Component
class IssueAssignTool(private val clients: DownstreamClients) : BaseTool(
    "issue_assign",
    "이슈 담당자 배정",
    "이슈에 담당자를 배정한다.",
    ToolSchemas.obj(
        "issueId" to ToolSchemas.integer("이슈 번호", 1),
        "assigneeId" to ToolSchemas.integer("담당자 사용자 번호", 1),
        required = listOf("issueId", "assigneeId"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val issueId = args.requireLong("issueId")
        val assigneeId = args.requireLong("assigneeId")
        val (data, text) = IssueValues.split(clients.operation(call.user).assignIssue(issueId, IssueAssignRequest(assigneeId), call.idempotencyKey))
        return ToolOutput(data = data, untrusted = text, message = "이슈 ${issueId}번 담당자를 ${assigneeId}번으로 정했습니다.")
    }
}

@Component
class IssueAddCommentTool(private val clients: DownstreamClients) : BaseTool(
    "issue_add_comment",
    "이슈 댓글",
    "이슈에 댓글을 남긴다. 에이전트가 쓰면 AGENT 댓글로 남는다.",
    ToolSchemas.obj(
        "issueId" to ToolSchemas.integer("이슈 번호", 1),
        "body" to ToolSchemas.string("댓글 내용", IssueValues.MAX_TEXT),
        required = listOf("issueId", "body"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val issueId = args.requireLong("issueId")
        val comment = clients.operation(call.user).commentIssue(issueId, IssueCommentRequest(args.requireText("body", IssueValues.MAX_TEXT)), call.idempotencyKey)
        return ToolOutput(data = ToolResponses.without(comment, "body"), untrusted = ToolResponses.pick(comment, "id", "body"), message = "이슈 ${issueId}번에 댓글을 남겼습니다.")
    }
}

@Component
class IssueResolveTool(private val clients: DownstreamClients) : BaseTool(
    "issue_resolve",
    "이슈 해결",
    "이슈를 해결 상태로 닫고 해결 내용을 남긴다.",
    ToolSchemas.obj(
        "issueId" to ToolSchemas.integer("이슈 번호", 1),
        "resolution" to ToolSchemas.string("해결 내용", IssueValues.MAX_TEXT),
        required = listOf("issueId", "resolution"),
    ),
) {
    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val issueId = args.requireLong("issueId")
        val issue = clients.operation(call.user).resolveIssue(issueId, IssueResolveRequest(args.requireText("resolution", IssueValues.MAX_TEXT)), call.idempotencyKey)
        val (data, text) = IssueValues.split(issue)
        return ToolOutput(data = data, untrusted = text, message = "이슈 ${issueId}번을 해결로 닫았습니다.")
    }
}

@Component
class ExposureHideContentTool(private val clients: DownstreamClients) : BaseTool(
    "exposure_hide_content",
    "작품 숨기기",
    "작품을 서비스에서 숨긴다. 승인을 거친 뒤에만 실행된다. 숨긴 작품은 새 편성에 넣을 수 없다.",
    ToolSchemas.obj(
        "contentId" to ToolSchemas.integer("작품 번호", 1),
        "issueId" to ToolSchemas.integer("근거가 된 이슈 번호", 1),
        "reason" to ToolSchemas.string("숨기는 이유. 승인자에게도 보인다", 2000),
        required = listOf("contentId", "issueId", "reason"),
    ),
) {
    override fun precheck(call: ToolCall) {
        call.arguments.requireLong("contentId")
        call.arguments.requireText("reason", 2000)
        clients.operation(call.user).issue(call.arguments.requireLong("issueId"))
    }

    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val contentId = args.requireLong("contentId")
        val exposure = clients.operation(call.user).hide(
            contentId,
            ExposureHideRequest(args.requireLong("issueId"), args.requireText("reason", 2000)),
            call.approvalId,
            call.idempotencyKey,
        )
        return ToolOutput(data = exposure, message = "작품 ${contentId}번을 숨겼습니다.")
    }
}

@Component
class ExposureRestoreContentTool(private val clients: DownstreamClients) : BaseTool(
    "exposure_restore_content",
    "숨긴 작품 다시 보이기",
    "숨긴 작품을 다시 보이게 한다. 승인을 거친 뒤에만 실행된다.",
    ToolSchemas.obj(
        "contentId" to ToolSchemas.integer("작품 번호", 1),
        "reason" to ToolSchemas.string("다시 보이게 하는 이유. 승인자에게도 보인다", 2000),
        required = listOf("contentId", "reason"),
    ),
) {
    override fun precheck(call: ToolCall) {
        call.arguments.requireLong("contentId")
        call.arguments.requireText("reason", 2000)
    }

    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val contentId = args.requireLong("contentId")
        val exposure = clients.operation(call.user).restore(
            contentId,
            ExposureRestoreRequest(args.requireText("reason", 2000)),
            call.approvalId,
            call.idempotencyKey,
        )
        return ToolOutput(data = exposure, message = "작품 ${contentId}번을 다시 보이게 했습니다.")
    }
}
