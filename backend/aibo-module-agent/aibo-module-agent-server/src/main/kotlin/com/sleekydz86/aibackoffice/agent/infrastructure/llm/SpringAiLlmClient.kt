package com.sleekydz86.aibackoffice.agent.infrastructure.llm

import com.sleekydz86.aibackoffice.agent.application.AgentToolSpec
import com.sleekydz86.aibackoffice.agent.application.LlmClient
import com.sleekydz86.aibackoffice.agent.application.LlmFailure
import com.sleekydz86.aibackoffice.agent.application.LlmMessage
import com.sleekydz86.aibackoffice.agent.application.LlmReply
import com.sleekydz86.aibackoffice.agent.application.LlmRequest
import com.sleekydz86.aibackoffice.agent.application.LlmToolCall
import com.sleekydz86.aibackoffice.agent.application.LlmUsage
import java.time.Duration
import java.util.concurrent.TimeoutException
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition

class SpringAiLlmClient(
    private val chatModel: ChatModel,
    private val provider: String,
    private val timeout: Duration,
) : LlmClient {

    override val modelName: String = chatModel.options.model?.takeIf { it.isNotBlank() } ?: provider

    override fun chat(request: LlmRequest, onToken: (String) -> Unit): LlmReply {
        val prompt = Prompt(messages(request), options(request.tools))
        val text = StringBuilder()
        val calls = LinkedHashMap<String, LlmToolCall>()
        var usage: LlmUsage? = null
        try {
            chatModel.stream(prompt)
                .doOnNext { response ->
                    collect(response, text, calls, onToken)
                    usage = usageOf(response) ?: usage
                }
                .blockLast(timeout)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Timeout", ignoreCase = true) == true || e.cause is TimeoutException) {
                throw LlmFailure(true, "모델 응답 시간이 지났습니다.", e)
            }
            throw LlmFailure(false, "모델 호출에 실패했습니다.", e)
        } catch (e: RuntimeException) {
            throw LlmFailure(false, "모델 호출에 실패했습니다.", e)
        }
        return LlmReply(text.toString(), calls.values.toList(), usage ?: LlmUsage(0, 0, modelName))
    }

    private fun collect(response: ChatResponse, text: StringBuilder, calls: MutableMap<String, LlmToolCall>, onToken: (String) -> Unit) {
        response.results.forEach { generation ->
            val output = generation.output
            output.text?.takeIf { it.isNotEmpty() }?.let {
                text.append(it)
                onToken(it)
            }
            output.toolCalls.forEach { call ->
                val id = call.id().ifBlank { "call-${calls.size + 1}" }
                if (call.name().isNotBlank()) calls[id] = LlmToolCall(id, call.name(), call.arguments().ifBlank { "{}" })
            }
        }
    }

    private fun usageOf(response: ChatResponse): LlmUsage? {
        val metadata = response.metadata
        val usage = metadata.usage
        val input = usage.promptTokens ?: 0
        val output = usage.completionTokens ?: 0
        if (input == 0 && output == 0) return null
        return LlmUsage(input, output, modelName)
    }

    private fun messages(request: LlmRequest): List<Message> = buildList {
        add(SystemMessage(request.systemPrompt))
        request.messages.forEach { message ->
            when (message) {
                is LlmMessage.User -> add(UserMessage(message.text))
                is LlmMessage.Assistant -> add(
                    AssistantMessage.builder()
                        .content(message.text)
                        .toolCalls(message.toolCalls.map { AssistantMessage.ToolCall(it.id, FUNCTION, it.name, it.argumentsJson) })
                        .build(),
                )
                is LlmMessage.ToolResults -> add(
                    ToolResponseMessage.builder()
                        .responses(message.results.map { ToolResponseMessage.ToolResponse(it.callId, it.name, it.content) })
                        .build(),
                )
            }
        }
    }

    private fun options(tools: List<AgentToolSpec>): ChatOptions {
        val builder = chatModel.options.mutate()
        if (builder is ToolCallingChatOptions.Builder<*>) {
            builder.toolCallbacks(tools.map(::DeclaredTool))
        }
        return builder.build()
    }

    private class DeclaredTool(spec: AgentToolSpec) : ToolCallback {
        private val definition = ToolDefinition.builder()
            .name(spec.name)
            .description(spec.description.ifBlank { spec.title ?: spec.name })
            .inputSchema(spec.inputSchemaJson)
            .build()

        override fun getToolDefinition(): ToolDefinition = definition

        override fun call(toolInput: String): String =
            throw UnsupportedOperationException("툴은 에이전트 반복에서만 실행합니다.")
    }

    companion object {
        private const val FUNCTION = "function"
    }
}
