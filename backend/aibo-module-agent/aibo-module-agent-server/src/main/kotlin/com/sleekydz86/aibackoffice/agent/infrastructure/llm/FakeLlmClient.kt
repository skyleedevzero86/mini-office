package com.sleekydz86.aibackoffice.agent.infrastructure.llm

import com.sleekydz86.aibackoffice.agent.application.LlmClient
import com.sleekydz86.aibackoffice.agent.application.LlmMessage
import com.sleekydz86.aibackoffice.agent.application.LlmReply
import com.sleekydz86.aibackoffice.agent.application.LlmRequest
import com.sleekydz86.aibackoffice.agent.application.LlmToolCall
import com.sleekydz86.aibackoffice.agent.application.LlmUsage
import tools.jackson.databind.json.JsonMapper

class FakeLlmClient(private val jsonMapper: JsonMapper) : LlmClient {

    override val modelName: String = MODEL

    override fun chat(request: LlmRequest, onToken: (String) -> Unit): LlmReply {
        val last = request.messages.lastOrNull()
        val reply = when (last) {
            is LlmMessage.ToolResults -> LlmReply(summary(last), emptyList(), LlmUsage(0, 0, MODEL))
            is LlmMessage.User -> answer(last.text, request)
            else -> LlmReply(help(request), emptyList(), LlmUsage(0, 0, MODEL))
        }
        if (reply.text.isNotEmpty()) chunks(reply.text).forEach(onToken)
        return reply.copy(usage = LlmUsage(estimate(request), estimate(reply.text), MODEL))
    }

    private fun answer(text: String, request: LlmRequest): LlmReply {
        val visible = request.tools.map { it.name }.toSet()
        val calls = text.lines()
            .map { it.trim() }
            .filter { it.startsWith(COMMAND) }
            .mapIndexedNotNull { index, line ->
                val body = line.removePrefix(COMMAND).trim()
                val name = body.substringBefore(' ').trim()
                if (name.isEmpty()) return@mapIndexedNotNull null
                val arguments = body.substringAfter(' ', "{}").trim().ifEmpty { "{}" }
                LlmToolCall("fake-${index + 1}", name, arguments)
            }
        if (calls.isEmpty()) return LlmReply(help(request), emptyList(), LlmUsage(0, 0, MODEL))
        val hidden = calls.map { it.name }.filterNot { it in visible }
        val note = if (hidden.isEmpty()) "툴 ${calls.size}개를 부르겠습니다." else "목록에 없는 툴도 그대로 불러 봅니다: ${hidden.joinToString(", ")}"
        return LlmReply(note, calls, LlmUsage(0, 0, MODEL))
    }

    private fun summary(results: LlmMessage.ToolResults): String = results.results.joinToString("\n") { result ->
        val parsed = try {
            jsonMapper.readValue(result.content, Map::class.java)
        } catch (e: RuntimeException) {
            null
        }
        val code = parsed?.get("code")?.toString() ?: "알 수 없음"
        val message = parsed?.get("message")?.toString().orEmpty()
        "${result.name} 결과는 $code 입니다. $message".trim()
    }

    private fun help(request: LlmRequest): String {
        val names = request.tools.take(HELP_TOOLS).joinToString(", ") { it.name }
        return "연습용 가짜 모델입니다. 툴을 부르려면 한 줄에 '/tool 툴이름 {\"값\":\"...\"}' 형식으로 적어 주세요. " +
            "지금 쓸 수 있는 툴은 ${request.tools.size}개입니다" + if (names.isEmpty()) "." else ": $names"
    }

    private fun chunks(text: String): List<String> = text.chunked(CHUNK)

    private fun estimate(request: LlmRequest): Int {
        val chars = request.systemPrompt.length + request.messages.sumOf { message ->
            when (message) {
                is LlmMessage.User -> message.text.length
                is LlmMessage.Assistant -> message.text.length + message.toolCalls.sumOf { it.argumentsJson.length }
                is LlmMessage.ToolResults -> message.results.sumOf { it.content.length }
            }
        }
        return estimate(chars)
    }

    private fun estimate(text: String): Int = estimate(text.length)

    private fun estimate(chars: Int): Int = (chars + 3) / 4

    companion object {
        const val MODEL = "fake"
        const val COMMAND = "/tool"
        private const val CHUNK = 12
        private const val HELP_TOOLS = 10
    }
}
