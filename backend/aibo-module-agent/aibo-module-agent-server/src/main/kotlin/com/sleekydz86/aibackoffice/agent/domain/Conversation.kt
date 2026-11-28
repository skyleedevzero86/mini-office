package com.sleekydz86.aibackoffice.agent.domain

import com.sleekydz86.aibackoffice.common.ErrorCode
import java.time.LocalDateTime

data class Conversation(
    val id: String,
    val userId: String,
    val title: String,
    val promptTemplateId: Long?,
    val createdAt: LocalDateTime,
) {
    fun ownedBy(userId: String): Boolean = this.userId == userId

    companion object {
        const val DEFAULT_TITLE = "새 대화"
        const val MAX_TITLE = 200

        fun titleOf(given: String?): String =
            given?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE) ?: DEFAULT_TITLE
    }
}

enum class MessageRole { USER, ASSISTANT, TOOL }

data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val model: String?,
    val createdAt: LocalDateTime,
)

enum class ToolCallStatus {
    OK, DENIED, PENDING_APPROVAL, ERROR;

    companion object {
        private val DENIED_CODES = setOf(
            ErrorCode.PERMISSION_DENIED.name,
            ErrorCode.SCOPE_DENIED.name,
            ErrorCode.TOOL_DISABLED.name,
            ErrorCode.APPROVAL_REQUIRED.name,
            ErrorCode.SELF_APPROVAL.name,
            ErrorCode.UNAUTHORIZED.name,
        )

        fun of(ok: Boolean, code: String?): ToolCallStatus = when {
            ok -> OK
            code == ErrorCode.PENDING_APPROVAL.name -> PENDING_APPROVAL
            code in DENIED_CODES -> DENIED
            else -> ERROR
        }
    }
}

data class ToolInvocationRecord(
    val messageId: Long,
    val toolName: String,
    val argumentsJson: String,
    val status: ToolCallStatus,
    val approvalId: Long?,
    val latencyMs: Int,
    val errorCode: String?,
    val traceId: String?,
)

data class PromptTemplate(
    val id: Long,
    val name: String,
    val version: Int,
    val systemPrompt: String,
)
