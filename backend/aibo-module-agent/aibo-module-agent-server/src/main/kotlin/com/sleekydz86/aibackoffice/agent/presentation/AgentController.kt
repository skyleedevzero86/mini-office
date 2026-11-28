package com.sleekydz86.aibackoffice.agent.presentation

import com.sleekydz86.aibackoffice.agent.application.AgentLoop
import com.sleekydz86.aibackoffice.agent.application.AgentToolView
import com.sleekydz86.aibackoffice.agent.application.ConversationService
import com.sleekydz86.aibackoffice.agent.application.ConversationView
import com.sleekydz86.aibackoffice.agent.application.MessageView
import com.sleekydz86.aibackoffice.agent.infrastructure.config.AgentProperties
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper

data class CreateConversationRequest(val title: String? = null)

data class SendMessageRequest(val content: String? = null)

@RestController
@RequestMapping("/api/agent")
class AgentController(
    private val conversations: ConversationService,
    private val loop: AgentLoop,
    private val executor: ExecutorService,
    private val jsonMapper: JsonMapper,
    private val properties: AgentProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/conversations")
    fun create(@RequestBody(required = false) request: CreateConversationRequest?): ConversationView =
        conversations.create(SecurityFrameworkUtils.loginUser(), request?.title)

    @GetMapping("/conversations")
    fun list(): List<ConversationView> = conversations.list(SecurityFrameworkUtils.loginUser())

    @GetMapping("/conversations/{id}/messages")
    fun messages(@PathVariable id: String): List<MessageView> = conversations.messages(SecurityFrameworkUtils.loginUser(), id)

    @PostMapping("/conversations/{id}/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun send(
        @PathVariable id: String,
        @RequestBody request: SendMessageRequest,
        response: HttpServletResponse,
    ): SseEmitter {
        val user = SecurityFrameworkUtils.loginUser()
        val traceId = TraceIds.current()
        val plan = conversations.prepareSend(user, id, request.content)
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        val emitter = SseEmitter(properties.streamTimeout.toMillis())
        val sink = SseAgentEventSink(emitter, jsonMapper)
        executor.execute {
            TraceIds.runWith(traceId) {
                try {
                    loop.run(user, plan.conversationId, plan.systemPrompt, traceId, sink)
                } catch (e: RuntimeException) {
                    log.error("에이전트 응답 중 오류가 났습니다. 대화 {}", plan.conversationId, e)
                    sink.error(ErrorCode.INTERNAL_ERROR.name, ErrorCode.INTERNAL_ERROR.defaultMessage)
                } finally {
                    sink.complete()
                }
            }
        }
        return emitter
    }

    @GetMapping("/tools")
    fun tools(): List<AgentToolView> = conversations.tools(SecurityFrameworkUtils.loginUser(), TraceIds.current())
}
