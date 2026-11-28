package com.sleekydz86.aibackoffice.mcp.application.tool

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.min

data class ToolDefinition(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
)

data class ToolCall(
    val user: LoginUser,
    val arguments: ToolArguments,
    val approvalId: Long?,
    val idempotencyKey: String?,
)

data class ToolOutput(
    val data: Any?,
    val untrusted: Any? = null,
    val message: String = "성공",
)

interface McpToolHandler {

    val definition: ToolDefinition

    fun scopeChannel(call: ToolCall): String? = null

    fun precheck(call: ToolCall) {}

    fun execute(call: ToolCall): ToolOutput
}

data class ToolList<T>(
    val items: List<T>,
    val total: Long,
    val truncated: Boolean,
) {
    companion object {
        const val MAX_ITEMS = 20

        fun <T> of(items: List<T>, total: Long): ToolList<T> {
            val shown = items.take(MAX_ITEMS)
            return ToolList(shown, total, total > shown.size)
        }

        fun limit(requested: Int?): Int = min(MAX_ITEMS, maxOf(1, requested ?: MAX_ITEMS))
    }
}

class ToolArguments(val values: Map<String, Any?>) {

    fun string(name: String): String? = (values[name] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    fun requireString(name: String): String =
        string(name) ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값이 필요합니다.")

    fun long(name: String): Long? = when (val value = values[name]) {
        null -> null
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 숫자여야 합니다.")
        else -> throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 숫자여야 합니다.")
    }

    fun requireLong(name: String): Long =
        long(name) ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값이 필요합니다.")

    fun int(name: String): Int? = long(name)?.toInt()

    fun boolean(name: String): Boolean? = when (val value = values[name]) {
        null -> null
        is Boolean -> value
        is String -> value.trim().lowercase().toBooleanStrictOrNull()
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 true 나 false 여야 합니다.")
        else -> throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 true 나 false 여야 합니다.")
    }

    fun channel(name: String = "channelCode"): String? = string(name)?.uppercase()

    fun requireChannel(name: String = "channelCode"): String =
        channel(name) ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값이 필요합니다.")

    fun dateTime(name: String): String? = string(name)?.also {
        try {
            OffsetDateTime.parse(it)
        } catch (exception: DateTimeParseException) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 2026-12-01T21:00:00+09:00 모양이어야 합니다.")
        }
    }

    fun requireDateTime(name: String): String =
        dateTime(name) ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값이 필요합니다.")

    fun choice(name: String, allowed: Set<String>): String? = string(name)?.uppercase()?.also {
        if (it !in allowed) throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 ${allowed.sorted().joinToString(", ")} 중 하나여야 합니다.")
    }

    fun requireChoice(name: String, allowed: Set<String>): String =
        choice(name, allowed) ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값이 필요합니다.")

    fun requireText(name: String, maxLength: Int): String = requireString(name).also {
        if (it.length > maxLength) throw BusinessException(ErrorCode.VALIDATION_FAILED, "$name 값은 ${maxLength}자를 넘을 수 없습니다.")
    }

    companion object {
        val EMPTY = ToolArguments(emptyMap())
    }
}
