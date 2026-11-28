package com.sleekydz86.aibackoffice.agent.support

import com.sleekydz86.aibackoffice.agent.application.AgentEventSink
import com.sleekydz86.aibackoffice.agent.application.AgentToolSpec
import com.sleekydz86.aibackoffice.agent.application.ConversationStore
import com.sleekydz86.aibackoffice.agent.application.LlmClient
import com.sleekydz86.aibackoffice.agent.application.LlmReply
import com.sleekydz86.aibackoffice.agent.application.LlmRequest
import com.sleekydz86.aibackoffice.agent.application.LlmUsage
import com.sleekydz86.aibackoffice.agent.application.StoredInvocation
import com.sleekydz86.aibackoffice.agent.application.ToolCallOutcome
import com.sleekydz86.aibackoffice.agent.application.ToolGateway
import com.sleekydz86.aibackoffice.agent.application.ToolSession
import com.sleekydz86.aibackoffice.agent.domain.ChatMessage
import com.sleekydz86.aibackoffice.agent.domain.Conversation
import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.agent.domain.ToolInvocationRecord
import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import java.time.LocalDateTime
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

object AgentFixtures {

    val jsonMapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    fun user(id: String = "7") =
        LoginUser(id, "사용자$id", null, emptySet(), setOf("issue:read"), setOf("TVN"), 0, "jti-$id", TokenType.USER, null, ActorType.HUMAN, "token-$id")

    fun conversation(id: String = "c-1", userId: String = "7", promptId: Long? = 1) =
        Conversation(id, userId, "대화", promptId, LocalDateTime.of(2026, 11, 28, 10, 0))

    fun ok(code: String = "OK", message: String = "성공") =
        ToolCallOutcome(true, code, message, null, "{\"ok\":true,\"code\":\"$code\",\"message\":\"$message\"}")

    fun pending(approvalId: Long) = ToolCallOutcome(
        false,
        "PENDING_APPROVAL",
        "승인 요청 ${approvalId}번을 올렸습니다.",
        approvalId,
        "{\"ok\":false,\"code\":\"PENDING_APPROVAL\",\"approvalId\":$approvalId}",
    )

    class MemoryStore : ConversationStore {
        val conversations = mutableMapOf<String, Conversation>()
        val messages = mutableListOf<ChatMessage>()
        val invocations = mutableListOf<ToolInvocationRecord>()

        override fun insert(conversation: Conversation) {
            conversations[conversation.id] = conversation
        }

        override fun find(id: String): Conversation? = conversations[id]

        override fun listByUser(userId: String, limit: Int): List<Conversation> =
            conversations.values.filter { it.userId == userId }.take(limit)

        override fun insertMessage(conversationId: String, role: MessageRole, content: String, usage: LlmUsage?): Long {
            val id = messages.size + 1L
            messages += ChatMessage(id, conversationId, role, content, usage?.inputTokens, usage?.outputTokens, usage?.model, LocalDateTime.of(2026, 11, 28, 10, 0))
            return id
        }

        override fun messages(conversationId: String): List<ChatMessage> = messages.filter { it.conversationId == conversationId }

        override fun recentMessages(conversationId: String, limit: Int): List<ChatMessage> = messages(conversationId).takeLast(limit)

        override fun insertInvocation(record: ToolInvocationRecord) {
            invocations += record
        }

        override fun invocations(messageIds: Collection<Long>): List<StoredInvocation> = invocations
            .filter { it.messageId in messageIds }
            .map { StoredInvocation(it.messageId, it.toolName, it.argumentsJson, it.status.name, it.approvalId, it.latencyMs, it.errorCode) }
    }

    class ScriptedLlm(private val steps: List<(LlmRequest, (String) -> Unit) -> LlmReply>) : LlmClient {
        val requests = mutableListOf<LlmRequest>()
        override val modelName: String = "scripted"

        override fun chat(request: LlmRequest, onToken: (String) -> Unit): LlmReply {
            requests += request
            val step = steps.getOrElse(requests.size - 1) { steps.last() }
            return step(request, onToken)
        }
    }

    class FakeSession(
        private val specs: List<AgentToolSpec> = listOf(AgentToolSpec("issue_search", "이슈 찾기", "이슈를 찾는다", "{\"type\":\"object\"}")),
        private val answer: (String, String) -> ToolCallOutcome = { _, _ -> ok() },
    ) : ToolSession {
        val calls = mutableListOf<Pair<String, String>>()
        var closed = false

        override fun tools(): List<AgentToolSpec> = specs

        override fun call(name: String, argumentsJson: String): ToolCallOutcome {
            calls += name to argumentsJson
            return answer(name, argumentsJson)
        }

        override fun close() {
            closed = true
        }
    }

    class Gateway(private val session: () -> ToolSession) : ToolGateway {
        val opened = mutableListOf<Triple<String, String?, String?>>()

        override fun open(user: LoginUser, conversationId: String?, traceId: String?): ToolSession {
            opened += Triple(user.rawToken, conversationId, traceId)
            return session()
        }
    }

    class RecordingSink : AgentEventSink {
        val events = mutableListOf<Pair<String, Any?>>()
        var cancel = false

        fun names(): List<String> = events.map { it.first }

        override fun token(text: String) {
            events += "token" to text
        }

        override fun toolCall(callId: String, name: String, arguments: Any?) {
            events += "tool_call" to name
        }

        override fun toolResult(callId: String, name: String, outcome: ToolCallOutcome) {
            events += "tool_result" to outcome.code
        }

        override fun approvalPending(approvalId: Long, toolName: String, reason: String) {
            events += "approval_pending" to approvalId
        }

        override fun done(messageId: Long, inputTokens: Int, outputTokens: Int, toolCalls: Int) {
            events += "done" to toolCalls
        }

        override fun error(code: String, message: String) {
            events += "error" to message
        }

        override fun cancelled(): Boolean = cancel
    }
}
