package com.sleekydz86.aibackoffice.agent.infrastructure.persistence

import com.sleekydz86.aibackoffice.agent.application.ConversationStore
import com.sleekydz86.aibackoffice.agent.application.LlmUsage
import com.sleekydz86.aibackoffice.agent.application.StoredInvocation
import com.sleekydz86.aibackoffice.agent.domain.ChatMessage
import com.sleekydz86.aibackoffice.agent.domain.Conversation
import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.agent.domain.ToolInvocationRecord
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import com.sleekydz86.aibackoffice.common.Zones
import java.time.Clock
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component

@Component
class JdbcConversationStore(
    private val jdbc: JdbcTemplate,
    private val clock: Clock = Zones.SEOUL_CLOCK,
) : ConversationStore {

    override fun insert(conversation: Conversation) {
        jdbc.update(
            "INSERT INTO conversations (id, user_id, title, prompt_template_id, created_at) VALUES (?, ?, ?, ?, ?)",
            conversation.id,
            conversation.userId,
            conversation.title,
            conversation.promptTemplateId,
            conversation.createdAt,
        )
    }

    override fun find(id: String): Conversation? =
        jdbc.query("$SELECT_CONVERSATION WHERE id = ?", ::mapConversation, id).firstOrNull()

    override fun listByUser(userId: String, limit: Int): List<Conversation> =
        jdbc.query("$SELECT_CONVERSATION WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?", ::mapConversation, userId, limit)

    override fun insertMessage(conversationId: String, role: MessageRole, content: String, usage: LlmUsage?): Long {
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                "INSERT INTO messages (conversation_id, role, content, input_tokens, output_tokens, model, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            ).apply {
                setString(1, conversationId)
                setString(2, role.name)
                setString(3, content)
                if (usage == null) setNull(4, Types.INTEGER) else setInt(4, usage.inputTokens)
                if (usage == null) setNull(5, Types.INTEGER) else setInt(5, usage.outputTokens)
                setString(6, usage?.model?.take(MAX_MODEL))
                setObject(7, LocalDateTime.now(clock))
            }
        }, keys)
        return keys.key?.toLong() ?: throw IllegalStateException("메시지 번호를 받지 못했습니다.")
    }

    override fun messages(conversationId: String): List<ChatMessage> =
        jdbc.query("$SELECT_MESSAGE WHERE conversation_id = ? ORDER BY id", ::mapMessage, conversationId)

    override fun recentMessages(conversationId: String, limit: Int): List<ChatMessage> =
        jdbc.query("$SELECT_MESSAGE WHERE conversation_id = ? ORDER BY id DESC LIMIT ?", ::mapMessage, conversationId, limit).reversed()

    override fun insertInvocation(record: ToolInvocationRecord) {
        jdbc.update(
            "INSERT INTO tool_invocations (message_id, tool_name, arguments, status, approval_id, latency_ms, error_code, trace_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            record.messageId,
            record.toolName,
            record.argumentsJson,
            record.status.name,
            record.approvalId,
            record.latencyMs,
            record.errorCode,
            record.traceId,
            LocalDateTime.now(clock),
        )
    }

    override fun invocations(messageIds: Collection<Long>): List<StoredInvocation> {
        if (messageIds.isEmpty()) return emptyList()
        val marks = messageIds.joinToString(",") { "?" }
        return jdbc.query(
            "SELECT message_id, tool_name, arguments, status, approval_id, latency_ms, error_code FROM tool_invocations " +
                "WHERE message_id IN ($marks) ORDER BY id",
            { rs, _ ->
                StoredInvocation(
                    messageId = rs.getLong("message_id"),
                    toolName = rs.getString("tool_name"),
                    argumentsJson = rs.getString("arguments"),
                    status = rs.getString("status"),
                    approvalId = rs.getLong("approval_id").takeUnless { rs.wasNull() },
                    latencyMs = rs.getInt("latency_ms"),
                    errorCode = rs.getString("error_code"),
                )
            },
            *messageIds.toTypedArray(),
        )
    }

    private fun mapConversation(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = Conversation(
        id = rs.getString("id"),
        userId = rs.getString("user_id"),
        title = rs.getString("title"),
        promptTemplateId = rs.getLong("prompt_template_id").takeUnless { rs.wasNull() },
        createdAt = rs.getObject("created_at", LocalDateTime::class.java),
    )

    private fun mapMessage(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = ChatMessage(
        id = rs.getLong("id"),
        conversationId = rs.getString("conversation_id"),
        role = MessageRole.valueOf(rs.getString("role")),
        content = rs.getString("content"),
        inputTokens = rs.getInt("input_tokens").takeUnless { rs.wasNull() },
        outputTokens = rs.getInt("output_tokens").takeUnless { rs.wasNull() },
        model = rs.getString("model"),
        createdAt = rs.getObject("created_at", LocalDateTime::class.java),
    )

    companion object {
        private const val MAX_MODEL = 100
        private const val SELECT_CONVERSATION = "SELECT id, user_id, title, prompt_template_id, created_at FROM conversations"
        private const val SELECT_MESSAGE =
            "SELECT id, conversation_id, role, content, input_tokens, output_tokens, model, created_at FROM messages"
    }
}
