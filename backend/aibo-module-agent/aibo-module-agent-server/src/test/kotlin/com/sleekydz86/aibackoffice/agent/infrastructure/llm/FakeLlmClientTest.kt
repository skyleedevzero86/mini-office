package com.sleekydz86.aibackoffice.agent.infrastructure.llm

import com.sleekydz86.aibackoffice.agent.application.AgentToolSpec
import com.sleekydz86.aibackoffice.agent.application.LlmMessage
import com.sleekydz86.aibackoffice.agent.application.LlmRequest
import com.sleekydz86.aibackoffice.agent.application.LlmToolResult
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FakeLlmClientTest {

    private val fake = FakeLlmClient(AgentFixtures.jsonMapper)
    private val tools = listOf(AgentToolSpec("issue_search", null, "이슈 찾기", "{}"))

    @Test
    @DisplayName("/tool 줄을 툴 호출로 바꾸고 글자를 나눠 보낸다")
    fun toolCommand() {
        // given
        val tokens = mutableListOf<String>()
        val request = LlmRequest("지시", listOf(LlmMessage.User("/tool issue_search {\"status\":\"OPEN\"}\n/tool approval_get")), tools)

        // when
        val reply = fake.chat(request) { tokens += it }

        // then
        assertEquals(listOf("issue_search", "approval_get"), reply.toolCalls.map { it.name })
        assertEquals("{\"status\":\"OPEN\"}", reply.toolCalls[0].argumentsJson)
        assertEquals("{}", reply.toolCalls[1].argumentsJson)
        assertEquals(reply.text, tokens.joinToString(""))
        assertTrue(reply.usage.inputTokens > 0)
    }

    @Test
    @DisplayName("툴 결과를 받으면 코드와 설명으로 답하고 더 부르지 않는다")
    fun summarizesResults() {
        // given
        val results = LlmMessage.ToolResults(listOf(LlmToolResult("fake-1", "issue_search", "{\"ok\":true,\"code\":\"OK\",\"message\":\"2건\"}")))
        val request = LlmRequest("지시", listOf(LlmMessage.User("/tool issue_search"), results), tools)

        // when
        val reply = fake.chat(request) {}

        // then
        assertTrue(reply.toolCalls.isEmpty())
        assertEquals("issue_search 결과는 OK 입니다. 2건", reply.text)
    }

    @Test
    @DisplayName("명령이 없으면 쓰는 법과 툴 개수를 알려 준다")
    fun help() {
        // when
        val reply = fake.chat(LlmRequest("지시", listOf(LlmMessage.User("안녕")), tools)) {}

        // then
        assertTrue(reply.toolCalls.isEmpty())
        assertTrue(reply.text.contains("1개"))
    }
}
