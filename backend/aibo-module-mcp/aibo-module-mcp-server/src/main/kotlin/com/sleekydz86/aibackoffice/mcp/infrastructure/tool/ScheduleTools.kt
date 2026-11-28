package com.sleekydz86.aibackoffice.mcp.infrastructure.tool

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolList
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolOutput
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolSchemas
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.DownstreamClients
import com.sleekydz86.aibackoffice.operation.api.SlotDraftRequest
import org.springframework.stereotype.Component

object ScheduleValues {
    val STATUSES = setOf("DRAFT", "PUBLISHED", "CANCELED")
}

@Component
class ScheduleListSlotsTool(private val clients: DownstreamClients) : BaseTool(
    "schedule_list_slots",
    "편성 목록",
    "채널과 기간으로 편성 슬롯을 찾는다. 채널을 비우면 맡은 채널 전체에서 찾는다.",
    ToolSchemas.obj(
        "channelCode" to ToolSchemas.string("채널 코드. 예 TVN", 20),
        "from" to ToolSchemas.dateTime("이 시각 뒤에 시작하는 편성"),
        "to" to ToolSchemas.dateTime("이 시각 전에 시작하는 편성"),
        "status" to ToolSchemas.choice("편성 상태", ScheduleValues.STATUSES.sorted()),
        ToolSchemas.limit(),
    ),
) {
    override fun scopeChannel(call: ToolCall): String? = call.arguments.channel()

    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val response = clients.operation(call.user).slots(
            args.channel(),
            args.dateTime("from"),
            args.dateTime("to"),
            args.choice("status", ScheduleValues.STATUSES),
            0,
            ToolList.limit(args.int("limit")),
        )
        val list = ToolResponses.list(response)
        return ToolResponses.listed(list, "편성 ${list.total}건 중 ${list.items.size}건을 찾았습니다.")
    }
}

@Component
class ScheduleFindConflictsTool(private val clients: DownstreamClients) : BaseTool(
    "schedule_find_conflicts",
    "편성 충돌 확인",
    "새 편성이 시간 겹침, 숨긴 작품, 밤 10시 등급 규칙에 걸리는지 미리 본다.",
    ToolSchemas.obj(
        "channelCode" to ToolSchemas.string("채널 코드", 20),
        "contentId" to ToolSchemas.integer("작품 번호", 1),
        "contentRating" to ToolSchemas.string("시청 등급. 예 ALL, 12, 15, 19", 10),
        "startAt" to ToolSchemas.dateTime("시작 시각"),
        "endAt" to ToolSchemas.dateTime("끝 시각"),
        "excludeSlotId" to ToolSchemas.integer("비교에서 뺄 슬롯 번호", 1),
        required = listOf("channelCode", "contentId", "startAt", "endAt"),
    ),
) {
    override fun scopeChannel(call: ToolCall): String = call.arguments.requireChannel()

    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val conflicts = clients.operation(call.user).conflicts(
            args.requireChannel(),
            args.requireLong("contentId"),
            args.string("contentRating"),
            args.requireDateTime("startAt"),
            args.requireDateTime("endAt"),
            args.long("excludeSlotId"),
        )
        val message = if (conflicts.isEmpty()) "충돌이 없습니다." else "충돌 ${conflicts.size}건이 있습니다."
        return ToolOutput(data = mapOf("conflicts" to conflicts, "count" to conflicts.size), message = message)
    }
}

@Component
class ScheduleCreateDraftTool(private val clients: DownstreamClients) : BaseTool(
    "schedule_create_draft",
    "편성 초안 만들기",
    "편성 초안을 만든다. 충돌이 있으면 만들지 않는다. 시작까지 두 시간이 안 남은 편성은 승인이 필요해 거절된다.",
    ToolSchemas.obj(
        "channelCode" to ToolSchemas.string("채널 코드", 20),
        "contentId" to ToolSchemas.integer("작품 번호", 1),
        "episodeId" to ToolSchemas.integer("에피소드 번호", 1),
        "contentRating" to ToolSchemas.string("시청 등급", 10),
        "startAt" to ToolSchemas.dateTime("시작 시각"),
        "endAt" to ToolSchemas.dateTime("끝 시각"),
        required = listOf("channelCode", "contentId", "startAt", "endAt"),
    ),
) {
    override fun scopeChannel(call: ToolCall): String = call.arguments.requireChannel()

    override fun execute(call: ToolCall): ToolOutput {
        val args = call.arguments
        val slot = clients.operation(call.user).createDraft(
            SlotDraftRequest(
                channelCode = args.requireChannel(),
                contentId = args.requireLong("contentId"),
                episodeId = args.long("episodeId"),
                contentRating = args.string("contentRating"),
                startAt = args.requireDateTime("startAt"),
                endAt = args.requireDateTime("endAt"),
            ),
            null,
            call.idempotencyKey,
        )
        return ToolOutput(data = slot, message = "편성 초안 ${slot["id"]}번을 만들었습니다.")
    }
}

abstract class SlotApprovalTool(
    name: String,
    title: String,
    description: String,
    protected val clients: DownstreamClients,
) : BaseTool(
    name,
    title,
    description,
    ToolSchemas.obj(
        "slotId" to ToolSchemas.integer("편성 슬롯 번호", 1),
        ToolSchemas.reason(),
        required = listOf("slotId"),
    ),
) {
    override fun scopeChannel(call: ToolCall): String? = slot(call)["channelCode"] as? String

    protected fun slot(call: ToolCall): Map<String, Any?> =
        clients.operation(call.user).slot(call.arguments.requireLong("slotId"))

    protected fun status(slot: Map<String, Any?>): String = slot["status"]?.toString().orEmpty()
}

@Component
class SchedulePublishSlotTool(clients: DownstreamClients) : SlotApprovalTool(
    "schedule_publish_slot",
    "편성 게시",
    "편성 초안을 게시한다. 승인을 거친 뒤에만 실행되고, 실행 직전에 충돌을 다시 본다.",
    clients,
) {
    override fun precheck(call: ToolCall) {
        val slot = slot(call)
        if (status(slot) != "DRAFT") {
            throw BusinessException(ErrorCode.INVALID_STATE, "초안 상태인 편성만 게시할 수 있습니다. 지금 상태는 ${status(slot)} 입니다.")
        }
        val conflicts = clients.operation(call.user).conflicts(
            slot["channelCode"].toString(),
            (slot["contentId"] as Number).toLong(),
            slot["contentRating"] as? String,
            slot["startAt"].toString(),
            slot["endAt"].toString(),
            (slot["id"] as Number).toLong(),
        )
        if (conflicts.isNotEmpty()) {
            throw BusinessException(ErrorCode.CONFLICT, conflicts.joinToString(" ") { it["message"].toString() })
        }
    }

    override fun execute(call: ToolCall): ToolOutput {
        val slotId = call.arguments.requireLong("slotId")
        val slot = clients.operation(call.user).publish(slotId, call.approvalId, call.idempotencyKey)
        return ToolOutput(data = slot, message = "편성 ${slotId}번을 게시했습니다.")
    }
}

@Component
class ScheduleCancelSlotTool(clients: DownstreamClients) : SlotApprovalTool(
    "schedule_cancel_slot",
    "편성 취소",
    "편성 슬롯을 취소한다. 승인을 거친 뒤에만 실행된다.",
    clients,
) {
    override fun precheck(call: ToolCall) {
        val slot = slot(call)
        if (status(slot) == "CANCELED") {
            throw BusinessException(ErrorCode.INVALID_STATE, "이미 취소된 편성입니다.")
        }
    }

    override fun execute(call: ToolCall): ToolOutput {
        val slotId = call.arguments.requireLong("slotId")
        val slot = clients.operation(call.user).cancel(slotId, call.approvalId, call.idempotencyKey)
        return ToolOutput(data = slot, message = "편성 ${slotId}번을 취소했습니다.")
    }
}
