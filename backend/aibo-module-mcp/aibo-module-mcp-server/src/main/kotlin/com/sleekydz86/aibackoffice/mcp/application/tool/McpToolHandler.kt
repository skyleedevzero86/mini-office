package com.sleekydz86.aibackoffice.mcp.application.tool

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
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

    fun scopeChannel(arguments: ToolArguments): String? = null

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

    companion object {
        val EMPTY = ToolArguments(emptyMap())
    }
}
