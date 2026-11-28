package com.sleekydz86.aibackoffice.agent.application

import com.sleekydz86.aibackoffice.agent.domain.ChatMessage
import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.agent.domain.ToolCallStatus
import com.sleekydz86.aibackoffice.agent.domain.ToolInvocationRecord
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

data class AgentLimits(
    val maxToolCalls: Int = 8,
    val historyMessages: Int = 20,
    val maxToolResultChars: Int = 8000,
)

class AgentLoop(
    private val llm: LlmClient,
    private val tools: ToolGateway,
    private val store: ConversationStore,
    private val jsonMapper: JsonMapper,
    private val limits: AgentLimits,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(user: LoginUser, conversationId: String, systemPrompt: String, traceId: String?, sink: AgentEventSink) {
        val session = try {
            tools.open(user, conversationId, traceId)
        } catch (e: RuntimeException) {
            log.warn("툴 서버에 연결하지 못했습니다. 대화 {}", conversationId, e)
            sink.error(ErrorCode.UPSTREAM_FAILED.name, TOOL_SERVER_FAILED)
            return
        }
        session.use { run(it, conversationId, systemPrompt, traceId, sink) }
    }

    private fun run(session: ToolSession, conversationId: String, systemPrompt: String, traceId: String?, sink: AgentEventSink) {
        val toolSpecs = try {
            session.tools()
        } catch (e: RuntimeException) {
            log.warn("툴 목록을 받지 못했습니다. 대화 {}", conversationId, e)
            sink.error(ErrorCode.UPSTREAM_FAILED.name, TOOL_SERVER_FAILED)
            return
        }
        val working = history(store.recentMessages(conversationId, limits.historyMessages)).toMutableList()
        var inputTokens = 0
        var outputTokens = 0
        var toolCalls = 0
        while (true) {
            if (sink.cancelled()) return
            val reply = callModel(LlmRequest(systemPrompt, working.toList(), toolSpecs), sink) ?: return
            inputTokens += reply.usage.inputTokens
            outputTokens += reply.usage.outputTokens
            val messageId = store.insertMessage(conversationId, MessageRole.ASSISTANT, reply.text, reply.usage)
            if (reply.toolCalls.isEmpty()) {
                sink.done(messageId, inputTokens, outputTokens, toolCalls)
                return
            }
            val remaining = limits.maxToolCalls - toolCalls
            if (remaining <= 0) {
                sink.token(LIMIT_MESSAGE)
                val limitId = store.insertMessage(conversationId, MessageRole.ASSISTANT, LIMIT_MESSAGE, null)
                sink.done(limitId, inputTokens, outputTokens, toolCalls)
                return
            }
            val runnable = reply.toolCalls.take(remaining)
            val skipped = reply.toolCalls.drop(remaining)
            val results = mutableListOf<LlmToolResult>()
            for (call in runnable) {
                if (sink.cancelled()) return
                toolCalls++
                results += execute(session, messageId, call, traceId, sink)
            }
            skipped.forEach { results += LlmToolResult(it.id, it.name, SKIPPED_RESULT) }
            working += LlmMessage.Assistant(reply.text, reply.toolCalls)
            working += LlmMessage.ToolResults(results)
        }
    }

    private fun execute(session: ToolSession, messageId: Long, call: LlmToolCall, traceId: String?, sink: AgentEventSink): LlmToolResult {
        sink.toolCall(call.id, call.name, parseArguments(call.argumentsJson))
        val started = System.nanoTime()
        val outcome = try {
            session.call(call.name, call.argumentsJson)
        } catch (e: RuntimeException) {
            log.warn("툴 호출에 실패했습니다. 툴 {}", call.name, e)
            ToolCallOutcome(false, ErrorCode.UPSTREAM_FAILED.name, TOOL_CALL_FAILED, null, failureJson(ErrorCode.UPSTREAM_FAILED.name, TOOL_CALL_FAILED))
        }
        val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
        val status = ToolCallStatus.of(outcome.ok, outcome.code)
        store.insertInvocation(
            ToolInvocationRecord(
                messageId = messageId,
                toolName = call.name.take(MAX_TOOL_NAME),
                argumentsJson = argumentsForStore(call.argumentsJson),
                status = status,
                approvalId = outcome.approvalId,
                latencyMs = latency,
                errorCode = if (outcome.ok) null else outcome.code.take(MAX_ERROR_CODE),
                traceId = traceId,
            ),
        )
        sink.toolResult(call.id, call.name, outcome)
        if (status == ToolCallStatus.PENDING_APPROVAL && outcome.approvalId != null) {
            sink.approvalPending(outcome.approvalId, call.name, outcome.message)
        }
        return LlmToolResult(call.id, call.name, outcome.content.take(limits.maxToolResultChars))
    }

    private fun callModel(request: LlmRequest, sink: AgentEventSink): LlmReply? {
        var attempt = 0
        while (true) {
            attempt++
            var streamed = false
            try {
                return llm.chat(request) { text ->
                    streamed = true
                    sink.token(text)
                }
            } catch (e: RuntimeException) {
                val timeout = (e as? LlmFailure)?.timeout == true
                if (!streamed && attempt < MAX_MODEL_ATTEMPTS && !sink.cancelled()) {
                    log.warn("모델 호출에 실패해 한 번 다시 시도합니다. 모델 {}", llm.modelName, e)
                    continue
                }
                log.warn("모델 호출에 실패했습니다. 모델 {}", llm.modelName, e)
                sink.error(ErrorCode.UPSTREAM_FAILED.name, if (timeout) MODEL_TIMEOUT else MODEL_FAILED)
                return null
            }
        }
    }

    private fun parseArguments(json: String): Any? = try {
        jsonMapper.readTree(json.ifBlank { "{}" })
    } catch (e: RuntimeException) {
        json
    }

    private fun argumentsForStore(json: String): String = try {
        jsonMapper.writeValueAsString(jsonMapper.readTree(json.ifBlank { "{}" }))
    } catch (e: RuntimeException) {
        jsonMapper.writeValueAsString(mapOf("raw" to json))
    }

    private fun failureJson(code: String, message: String): String =
        jsonMapper.writeValueAsString(mapOf("ok" to false, "code" to code, "message" to message))

    companion object {
        const val MAX_MODEL_ATTEMPTS = 2
        const val MAX_TOOL_NAME = 60
        const val MAX_ERROR_CODE = 30
        const val TOOL_SERVER_FAILED = "툴 서버에 연결하지 못했습니다. 잠시 뒤 다시 시도하세요."
        const val TOOL_CALL_FAILED = "툴 서버 호출에 실패했습니다."
        const val MODEL_FAILED = "모델 호출에 실패했습니다. 잠시 뒤 다시 시도하세요."
        const val MODEL_TIMEOUT = "모델 응답 시간이 지났습니다. 잠시 뒤 다시 시도하세요."
        const val LIMIT_MESSAGE = "한 요청에서 툴은 여덟 번까지만 부를 수 있어 여기서 멈췄습니다. 이어서 진행할지 알려 주세요."
        const val SKIPPED_RESULT =
            "{\"ok\":false,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"한 요청의 툴 호출 한도를 넘어 실행하지 않았습니다.\"}"

        fun history(messages: List<ChatMessage>): List<LlmMessage> {
            val result = mutableListOf<LlmMessage>()
            for (message in messages) {
                when (message.role) {
                    MessageRole.USER -> {
                        val last = result.lastOrNull()
                        if (last is LlmMessage.User) {
                            result[result.lastIndex] = LlmMessage.User(last.text + "\n" + message.content)
                        } else {
                            result += LlmMessage.User(message.content)
                        }
                    }
                    MessageRole.ASSISTANT -> {
                        if (message.content.isBlank() || result.isEmpty()) continue
                        val last = result.last()
                        if (last is LlmMessage.Assistant) {
                            result[result.lastIndex] = LlmMessage.Assistant(last.text + "\n" + message.content)
                        } else {
                            result += LlmMessage.Assistant(message.content)
                        }
                    }
                    MessageRole.TOOL -> Unit
                }
            }
            return result
        }
    }
}
