package com.sleekydz86.aibackoffice.agent.application

import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.agent.domain.ToolCallStatus
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.FakeSession
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.Gateway
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.MemoryStore
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.RecordingSink
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.ScriptedLlm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AgentLoopTest {

    private val store = MemoryStore()
    private val sink = RecordingSink()
    private val user = AgentFixtures.user()

    private fun reply(text: String, vararg calls: LlmToolCall) = LlmReply(text, calls.toList(), LlmUsage(10, 5, "scripted"))

    private fun loop(llm: LlmClient, gateway: ToolGateway, maxToolCalls: Int = 8) =
        AgentLoop(llm, gateway, store, AgentFixtures.jsonMapper, AgentLimits(maxToolCalls = maxToolCalls))

    private fun start(text: String) {
        store.insert(AgentFixtures.conversation())
        store.insertMessage("c-1", MessageRole.USER, text, null)
    }

    @Test
    @DisplayName("모델이 툴을 부르면 결과를 넘겨 다시 묻고 마지막 답으로 끝낸다")
    fun toolThenAnswer() {
        // given
        start("열린 이슈 알려 줘")
        val session = FakeSession()
        val gateway = Gateway { session }
        val llm = ScriptedLlm(
            listOf(
                { _, token -> token("찾아볼게요"); reply("찾아볼게요", LlmToolCall("call-1", "issue_search", "{\"status\":\"OPEN\"}")) },
                { _, token -> token("열린 이슈는 2건입니다"); reply("열린 이슈는 2건입니다") },
            ),
        )

        // when
        loop(llm, gateway).run(user, "c-1", "지시", "trace-1", sink)

        // then
        assertEquals(listOf("token", "tool_call", "tool_result", "token", "done"), sink.names())
        assertEquals(listOf("issue_search" to "{\"status\":\"OPEN\"}"), session.calls)
        assertEquals(Triple("token-7", "c-1", "trace-1"), gateway.opened.single())
        assertTrue(session.closed)
        val second = llm.requests[1].messages
        assertTrue(second[second.lastIndex - 1] is LlmMessage.Assistant)
        assertEquals("call-1", (second.last() as LlmMessage.ToolResults).results.single().callId)
        val invocation = store.invocations.single()
        assertEquals(ToolCallStatus.OK, invocation.status)
        assertEquals("trace-1", invocation.traceId)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.ASSISTANT), store.messages.map { it.role })
        assertEquals(20, store.messages.mapNotNull { it.inputTokens }.sum())
    }

    @Test
    @DisplayName("승인 대기 결과면 approval_pending 을 보내고 승인 번호를 기록한다")
    fun pendingApproval() {
        // given
        start("작품 1번 숨겨 줘")
        val session = FakeSession { _, _ -> AgentFixtures.pending(91) }
        val llm = ScriptedLlm(
            listOf(
                { _, _ -> reply("", LlmToolCall("call-1", "exposure_hide_content", "{\"titleId\":1,\"issueId\":3}")) },
                { _, _ -> reply("승인 요청 91번을 올렸습니다") },
            ),
        )

        // when
        loop(llm, Gateway { session }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(listOf("tool_call", "tool_result", "approval_pending", "done"), sink.names())
        assertEquals(91L, sink.events.first { it.first == "approval_pending" }.second)
        val invocation = store.invocations.single()
        assertEquals(ToolCallStatus.PENDING_APPROVAL, invocation.status)
        assertEquals(91L, invocation.approvalId)
        assertEquals("PENDING_APPROVAL", invocation.errorCode)
    }

    @Test
    @DisplayName("툴은 한 요청에서 여덟 번까지만 부르고 더 부르려 하면 멈추고 묻는다")
    fun stopsAtEightCalls() {
        // given
        start("계속 찾아 줘")
        val session = FakeSession()
        var seq = 0
        val llm = ScriptedLlm(listOf({ _, _ -> seq++; reply("", LlmToolCall("call-$seq", "issue_search", "{}")) }))

        // when
        loop(llm, Gateway { session }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(8, session.calls.size)
        assertEquals(9, llm.requests.size)
        assertEquals("done" to 8, sink.events.last())
        assertTrue(sink.events.contains("token" to AgentLoop.LIMIT_MESSAGE))
        assertEquals(AgentLoop.LIMIT_MESSAGE, store.messages.last().content)
    }

    @Test
    @DisplayName("한 번에 한도를 넘는 툴을 요청하면 남은 횟수만 실행하고 나머지는 실행하지 않았다고 알린다")
    fun skipsCallsOverLimit() {
        // given
        start("세 개 찾아 줘")
        val session = FakeSession()
        val llm = ScriptedLlm(
            listOf(
                { _, _ -> reply("", LlmToolCall("a", "issue_search", "{}"), LlmToolCall("b", "issue_search", "{}"), LlmToolCall("c", "issue_search", "{}")) },
                { _, _ -> reply("두 건만 찾았습니다") },
            ),
        )

        // when
        loop(llm, Gateway { session }, maxToolCalls = 2).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(2, session.calls.size)
        val results = (llm.requests[1].messages.last() as LlmMessage.ToolResults).results
        assertEquals(listOf("a", "b", "c"), results.map { it.callId })
        assertEquals(AgentLoop.SKIPPED_RESULT, results.last().content)
        assertEquals("done" to 2, sink.events.last())
    }

    @Test
    @DisplayName("모델 호출이 실패하면 한 번만 다시 시도한다")
    fun retriesOnce() {
        // given
        start("안녕")
        val llm = ScriptedLlm(
            listOf(
                { _, _ -> throw LlmFailure(false, "연결 실패") },
                { _, token -> token("안녕하세요"); reply("안녕하세요") },
            ),
        )

        // when
        loop(llm, Gateway { FakeSession() }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(2, llm.requests.size)
        assertEquals(listOf("token", "done"), sink.names())
    }

    @Test
    @DisplayName("두 번 다 실패하거나 시간이 지나면 오류를 그대로 알리고 더 시도하지 않는다")
    fun reportsModelFailure() {
        // given
        start("안녕")
        val llm = ScriptedLlm(listOf({ _, _ -> throw LlmFailure(true, "시간 초과") }))

        // when
        loop(llm, Gateway { FakeSession() }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(2, llm.requests.size)
        assertEquals(listOf("error" to AgentLoop.MODEL_TIMEOUT), sink.events)
        assertEquals(listOf(MessageRole.USER), store.messages.map { it.role })
    }

    @Test
    @DisplayName("글자를 보내기 시작한 뒤 실패하면 같은 글이 두 번 나가지 않게 다시 시도하지 않는다")
    fun noRetryAfterTokens() {
        // given
        start("안녕")
        val llm = ScriptedLlm(listOf({ _, token -> token("안녕"); throw LlmFailure(false, "끊김") }))

        // when
        loop(llm, Gateway { FakeSession() }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(1, llm.requests.size)
        assertEquals(listOf("token", "error"), sink.names())
    }

    @Test
    @DisplayName("툴 서버에 붙지 못하면 모델을 부르지 않고 오류로 끝낸다")
    fun toolServerDown() {
        // given
        start("안녕")
        val llm = ScriptedLlm(listOf({ _, _ -> reply("안녕") }))

        // when
        loop(llm, Gateway { throw IllegalStateException("연결 거부") }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(0, llm.requests.size)
        assertEquals(listOf("error" to AgentLoop.TOOL_SERVER_FAILED), sink.events)
    }

    @Test
    @DisplayName("툴 호출 중 예외가 나도 오류 결과를 모델에 넘기고 기록한다")
    fun toolCallException() {
        // given
        start("찾아 줘")
        val session = FakeSession { _, _ -> throw IllegalStateException("끊김") }
        val llm = ScriptedLlm(
            listOf(
                { _, _ -> reply("", LlmToolCall("call-1", "issue_search", "{}")) },
                { _, _ -> reply("툴 서버가 응답하지 않았습니다") },
            ),
        )

        // when
        loop(llm, Gateway { session }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(ToolCallStatus.ERROR, store.invocations.single().status)
        assertEquals("UPSTREAM_FAILED", store.invocations.single().errorCode)
        assertEquals("done" to 1, sink.events.last())
    }

    @Test
    @DisplayName("사용자가 연결을 끊으면 다음 단계로 가지 않는다")
    fun stopsWhenCancelled() {
        // given
        start("찾아 줘")
        val session = FakeSession()
        val llm = ScriptedLlm(listOf({ _, _ -> sink.cancel = true; reply("", LlmToolCall("call-1", "issue_search", "{}")) }))

        // when
        loop(llm, Gateway { session }).run(user, "c-1", "지시", null, sink)

        // then
        assertEquals(0, session.calls.size)
        assertTrue(session.closed)
    }

    @Test
    @DisplayName("기록을 모델에 넣을 때 빈 답은 빼고 같은 역할이 이어지면 하나로 합친다")
    fun historyMerge() {
        // given
        store.insert(AgentFixtures.conversation())
        store.insertMessage("c-1", MessageRole.ASSISTANT, "앞이 잘린 답", null)
        store.insertMessage("c-1", MessageRole.USER, "첫 질문", null)
        store.insertMessage("c-1", MessageRole.ASSISTANT, "", null)
        store.insertMessage("c-1", MessageRole.ASSISTANT, "답 하나", null)
        store.insertMessage("c-1", MessageRole.ASSISTANT, "답 둘", null)
        store.insertMessage("c-1", MessageRole.USER, "다시 질문", null)
        store.insertMessage("c-1", MessageRole.USER, "또 질문", null)

        // when
        val history = AgentLoop.history(store.messages("c-1"))

        // then
        assertEquals(
            listOf(LlmMessage.User("첫 질문"), LlmMessage.Assistant("답 하나\n답 둘"), LlmMessage.User("다시 질문\n또 질문")),
            history,
        )
    }
}
