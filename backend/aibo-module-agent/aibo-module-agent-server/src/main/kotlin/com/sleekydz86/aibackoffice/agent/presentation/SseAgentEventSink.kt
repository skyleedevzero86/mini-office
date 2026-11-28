package com.sleekydz86.aibackoffice.agent.presentation

import com.sleekydz86.aibackoffice.agent.application.AgentEventSink
import com.sleekydz86.aibackoffice.agent.application.ToolCallOutcome
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper

class SseAgentEventSink(
    private val emitter: SseEmitter,
    private val jsonMapper: JsonMapper,
) : AgentEventSink {
    private val log = LoggerFactory.getLogger(javaClass)
    private val closed = AtomicBoolean(false)

    init {
        emitter.onTimeout { closed.set(true) }
        emitter.onError { closed.set(true) }
        emitter.onCompletion { closed.set(true) }
    }

    override fun token(text: String) = send("token", mapOf("text" to text))

    override fun toolCall(callId: String, name: String, arguments: Any?) =
        send("tool_call", mapOf("callId" to callId, "name" to name, "arguments" to arguments))

    override fun toolResult(callId: String, name: String, outcome: ToolCallOutcome) = send(
        "tool_result",
        buildMap {
            put("callId", callId)
            put("name", name)
            put("ok", outcome.ok)
            put("code", outcome.code)
            put("message", outcome.message)
            outcome.approvalId?.let { put("approvalId", it) }
        },
    )

    override fun approvalPending(approvalId: Long, toolName: String, reason: String) =
        send("approval_pending", mapOf("approvalId" to approvalId, "toolName" to toolName, "reason" to reason))

    override fun done(messageId: Long, inputTokens: Int, outputTokens: Int, toolCalls: Int) = send(
        "done",
        mapOf("messageId" to messageId, "inputTokens" to inputTokens, "outputTokens" to outputTokens, "toolCalls" to toolCalls),
    )

    override fun error(code: String, message: String) = send("error", mapOf("code" to code, "message" to message))

    override fun cancelled(): Boolean = closed.get()

    fun complete() {
        if (closed.compareAndSet(false, true)) emitter.complete()
    }

    @Synchronized
    private fun send(event: String, data: Any) {
        if (closed.get()) return
        try {
            emitter.send(SseEmitter.event().name(event).data(jsonMapper.writeValueAsString(data), MediaType.TEXT_PLAIN))
        } catch (e: IOException) {
            closed.set(true)
            log.debug("사용자 연결이 끊겨 실시간 응답을 멈춥니다.", e)
        } catch (e: IllegalStateException) {
            closed.set(true)
            log.debug("이미 끝난 실시간 응답에 보내려 했습니다.", e)
        }
    }
}
