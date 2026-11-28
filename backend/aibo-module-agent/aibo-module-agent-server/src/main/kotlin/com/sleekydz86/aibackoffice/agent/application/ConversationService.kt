package com.sleekydz86.aibackoffice.agent.application

import com.sleekydz86.aibackoffice.agent.domain.Conversation
import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import tools.jackson.databind.json.JsonMapper

data class ConversationView(val id: String, val title: String, val createdAt: OffsetDateTime)

data class ToolInvocationView(
    val toolName: String,
    val arguments: Any?,
    val status: String,
    val approvalId: Long?,
    val latencyMs: Int,
    val errorCode: String?,
)

data class MessageView(
    val id: Long,
    val role: String,
    val content: String,
    val model: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val createdAt: OffsetDateTime,
    val toolInvocations: List<ToolInvocationView>,
)

data class AgentToolView(val name: String, val title: String?, val description: String, val inputSchema: Any?)

data class SendPlan(val conversationId: String, val systemPrompt: String)

class ConversationService(
    private val store: ConversationStore,
    private val prompts: PromptStore,
    private val limiter: RequestLimiter,
    private val tools: ToolGateway,
    private val jsonMapper: JsonMapper,
    private val promptName: String,
    private val maxMessageChars: Int,
    private val clock: Clock = Zones.SEOUL_CLOCK,
) {

    fun create(user: LoginUser, title: String?): ConversationView {
        val prompt = prompts.active(promptName)
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            userId = user.userId,
            title = Conversation.titleOf(title),
            promptTemplateId = prompt?.id,
            createdAt = LocalDateTime.now(clock),
        )
        store.insert(conversation)
        return view(conversation)
    }

    fun list(user: LoginUser): List<ConversationView> = store.listByUser(user.userId, MAX_CONVERSATIONS).map(::view)

    fun messages(user: LoginUser, conversationId: String): List<MessageView> {
        owned(user, conversationId)
        val messages = store.messages(conversationId)
        val invocations = store.invocations(messages.map { it.id }).groupBy { it.messageId }
        return messages.map { message ->
            MessageView(
                id = message.id,
                role = message.role.name,
                content = message.content,
                model = message.model,
                inputTokens = message.inputTokens,
                outputTokens = message.outputTokens,
                createdAt = offset(message.createdAt),
                toolInvocations = invocations[message.id].orEmpty().map { stored ->
                    ToolInvocationView(
                        toolName = stored.toolName,
                        arguments = stored.argumentsJson?.let(::readJson),
                        status = stored.status,
                        approvalId = stored.approvalId,
                        latencyMs = stored.latencyMs,
                        errorCode = stored.errorCode,
                    )
                },
            )
        }
    }

    fun prepareSend(user: LoginUser, conversationId: String, content: String?): SendPlan {
        val text = content?.trim().orEmpty()
        if (text.isEmpty()) throw BusinessException(ErrorCode.VALIDATION_FAILED, "보낼 내용을 입력하세요.")
        if (text.length > maxMessageChars) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "메시지는 ${maxMessageChars}자까지 보낼 수 있습니다.")
        }
        val conversation = owned(user, conversationId)
        limiter.acquire(user.userId)
        val prompt = conversation.promptTemplateId?.let(prompts::find) ?: prompts.active(promptName)
            ?: throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "에이전트 기본 지시가 없습니다. prompt_templates를 확인하세요.")
        store.insertMessage(conversation.id, MessageRole.USER, text, null)
        return SendPlan(conversation.id, prompt.systemPrompt)
    }

    fun tools(user: LoginUser, traceId: String?): List<AgentToolView> = try {
        tools.open(user, null, traceId).use { session ->
            session.tools().map { AgentToolView(it.name, it.title, it.description, readJson(it.inputSchemaJson)) }
        }
    } catch (e: BusinessException) {
        throw e
    } catch (e: RuntimeException) {
        throw BusinessException(ErrorCode.UPSTREAM_FAILED, AgentLoop.TOOL_SERVER_FAILED)
    }

    private fun owned(user: LoginUser, conversationId: String): Conversation {
        val conversation = store.find(conversationId)
        if (conversation == null || !conversation.ownedBy(user.userId)) {
            throw BusinessException(ErrorCode.NOT_FOUND, "대화를 찾을 수 없습니다.")
        }
        return conversation
    }

    private fun readJson(json: String): Any? = try {
        jsonMapper.readTree(json)
    } catch (e: RuntimeException) {
        json
    }

    private fun view(conversation: Conversation) = ConversationView(conversation.id, conversation.title, offset(conversation.createdAt))

    private fun offset(value: LocalDateTime): OffsetDateTime = value.atZone(Zones.SEOUL).toOffsetDateTime()

    companion object {
        const val MAX_CONVERSATIONS = 100
    }
}
