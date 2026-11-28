package com.sleekydz86.aibackoffice.agent.application

import com.sleekydz86.aibackoffice.agent.domain.ChatMessage
import com.sleekydz86.aibackoffice.agent.domain.Conversation
import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.agent.domain.PromptTemplate
import com.sleekydz86.aibackoffice.agent.domain.ToolInvocationRecord
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser

interface ConversationStore {
    fun insert(conversation: Conversation)
    fun find(id: String): Conversation?
    fun listByUser(userId: String, limit: Int): List<Conversation>
    fun insertMessage(conversationId: String, role: MessageRole, content: String, usage: LlmUsage?): Long
    fun messages(conversationId: String): List<ChatMessage>
    fun recentMessages(conversationId: String, limit: Int): List<ChatMessage>
    fun insertInvocation(record: ToolInvocationRecord)
    fun invocations(messageIds: Collection<Long>): List<StoredInvocation>
}

data class StoredInvocation(
    val messageId: Long,
    val toolName: String,
    val argumentsJson: String?,
    val status: String,
    val approvalId: Long?,
    val latencyMs: Int,
    val errorCode: String?,
)

interface PromptStore {
    fun active(name: String): PromptTemplate?
    fun find(id: Long): PromptTemplate?
}

fun interface RequestLimiter {
    fun acquire(userId: String)
}

fun interface ToolGateway {
    fun open(user: LoginUser, conversationId: String?, traceId: String?): ToolSession
}

interface ToolSession : AutoCloseable {
    fun tools(): List<AgentToolSpec>
    fun call(name: String, argumentsJson: String): ToolCallOutcome
    override fun close()
}

data class AgentToolSpec(
    val name: String,
    val title: String?,
    val description: String,
    val inputSchemaJson: String,
)

data class ToolCallOutcome(
    val ok: Boolean,
    val code: String,
    val message: String,
    val approvalId: Long?,
    val content: String,
)

interface LlmClient {
    val modelName: String
    fun chat(request: LlmRequest, onToken: (String) -> Unit): LlmReply
}

data class LlmRequest(
    val systemPrompt: String,
    val messages: List<LlmMessage>,
    val tools: List<AgentToolSpec>,
)

sealed interface LlmMessage {
    data class User(val text: String) : LlmMessage
    data class Assistant(val text: String, val toolCalls: List<LlmToolCall> = emptyList()) : LlmMessage
    data class ToolResults(val results: List<LlmToolResult>) : LlmMessage
}

data class LlmToolCall(val id: String, val name: String, val argumentsJson: String)

data class LlmToolResult(val callId: String, val name: String, val content: String)

data class LlmUsage(val inputTokens: Int, val outputTokens: Int, val model: String?)

data class LlmReply(
    val text: String,
    val toolCalls: List<LlmToolCall>,
    val usage: LlmUsage,
)

class LlmFailure(val timeout: Boolean, message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface AgentEventSink {
    fun token(text: String)
    fun toolCall(callId: String, name: String, arguments: Any?)
    fun toolResult(callId: String, name: String, outcome: ToolCallOutcome)
    fun approvalPending(approvalId: Long, toolName: String, reason: String)
    fun done(messageId: Long, inputTokens: Int, outputTokens: Int, toolCalls: Int)
    fun error(code: String, message: String)
    fun cancelled(): Boolean
}
