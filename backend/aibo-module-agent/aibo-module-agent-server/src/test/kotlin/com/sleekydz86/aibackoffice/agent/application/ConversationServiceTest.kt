package com.sleekydz86.aibackoffice.agent.application

import com.sleekydz86.aibackoffice.agent.domain.MessageRole
import com.sleekydz86.aibackoffice.agent.domain.PromptTemplate
import com.sleekydz86.aibackoffice.agent.domain.ToolCallStatus
import com.sleekydz86.aibackoffice.agent.domain.ToolInvocationRecord
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.FakeSession
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.Gateway
import com.sleekydz86.aibackoffice.agent.support.AgentFixtures.MemoryStore
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import tools.jackson.databind.JsonNode

class ConversationServiceTest {

    private val store = MemoryStore()
    private val prompts = mock<PromptStore> {
        on { active("ops-agent") } doReturn PromptTemplate(2, "ops-agent", 2, "둘째 지시")
        on { find(1) } doReturn PromptTemplate(1, "ops-agent", 1, "첫째 지시")
    }
    private val limiter = mock<RequestLimiter>()
    private val session = FakeSession()
    private val service = ConversationService(store, prompts, limiter, Gateway { session }, AgentFixtures.jsonMapper, "ops-agent", 100)

    @Test
    @DisplayName("대화를 만들면 지금 쓰는 프롬프트 버전을 붙이고 제목이 없으면 새 대화로 둔다")
    fun create() {
        // when
        val view = service.create(AgentFixtures.user(), "  ")

        // then
        assertEquals("새 대화", view.title)
        assertEquals(2L, store.conversations.getValue(view.id).promptTemplateId)
        assertEquals("7", store.conversations.getValue(view.id).userId)
    }

    @Test
    @DisplayName("남의 대화와 없는 대화는 같은 NOT_FOUND 로 거절한다")
    fun othersConversationHidden() {
        // given
        store.insert(AgentFixtures.conversation(id = "c-1", userId = "8"))

        // when
        val other = assertThrows(BusinessException::class.java) { service.messages(AgentFixtures.user("7"), "c-1") }
        val missing = assertThrows(BusinessException::class.java) { service.messages(AgentFixtures.user("7"), "c-404") }
        val send = assertThrows(BusinessException::class.java) { service.prepareSend(AgentFixtures.user("7"), "c-1", "안녕") }

        // then
        assertEquals(ErrorCode.NOT_FOUND, other.code)
        assertEquals(other.code, missing.code)
        assertEquals(other.message, missing.message)
        assertEquals(ErrorCode.NOT_FOUND, send.code)
        verify(limiter, never()).acquire(any())
        assertTrue(store.messages.isEmpty())
    }

    @Test
    @DisplayName("보내기 전에 요청 횟수를 세고 질문을 저장하며 대화를 만들 때의 프롬프트 버전을 쓴다")
    fun prepareSend() {
        // given
        store.insert(AgentFixtures.conversation(promptId = 1))

        // when
        val plan = service.prepareSend(AgentFixtures.user(), "c-1", "  열린 이슈  ")

        // then
        verify(limiter).acquire("7")
        assertEquals("첫째 지시", plan.systemPrompt)
        assertEquals("열린 이슈", store.messages.single().content)
        assertEquals(MessageRole.USER, store.messages.single().role)
    }

    @Test
    @DisplayName("요청 횟수를 넘으면 질문을 저장하지 않는다")
    fun rateLimited() {
        // given
        store.insert(AgentFixtures.conversation())
        val limited = mock<RequestLimiter> { on { acquire("7") } doThrow BusinessException(ErrorCode.TOO_MANY_REQUESTS) }
        val service = ConversationService(store, prompts, limited, Gateway { session }, AgentFixtures.jsonMapper, "ops-agent", 100)

        // when
        val error = assertThrows(BusinessException::class.java) { service.prepareSend(AgentFixtures.user(), "c-1", "안녕") }

        // then
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, error.code)
        assertTrue(store.messages.isEmpty())
    }

    @Test
    @DisplayName("빈 내용과 너무 긴 내용은 거절한다")
    fun invalidContent() {
        // given
        store.insert(AgentFixtures.conversation())

        // when
        val blank = assertThrows(BusinessException::class.java) { service.prepareSend(AgentFixtures.user(), "c-1", " ") }
        val long = assertThrows(BusinessException::class.java) { service.prepareSend(AgentFixtures.user(), "c-1", "가".repeat(101)) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, blank.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, long.code)
    }

    @Test
    @DisplayName("메시지 조회는 툴 호출 기록을 메시지에 붙여 돌려준다")
    fun messagesWithInvocations() {
        // given
        store.insert(AgentFixtures.conversation())
        store.insertMessage("c-1", MessageRole.USER, "숨겨 줘", null)
        val answer = store.insertMessage("c-1", MessageRole.ASSISTANT, "", LlmUsage(10, 2, "fake"))
        store.insertInvocation(ToolInvocationRecord(answer, "exposure_hide_content", "{\"titleId\":1}", ToolCallStatus.PENDING_APPROVAL, 91, 12, "PENDING_APPROVAL", "t-1"))

        // when
        val messages = service.messages(AgentFixtures.user(), "c-1")

        // then
        assertEquals(listOf("USER", "ASSISTANT"), messages.map { it.role })
        val invocation = messages[1].toolInvocations.single()
        assertEquals("PENDING_APPROVAL", invocation.status)
        assertEquals(91L, invocation.approvalId)
        assertEquals(1, (invocation.arguments as JsonNode).get("titleId").asInt())
        assertTrue(messages[0].toolInvocations.isEmpty())
    }

    @Test
    @DisplayName("툴 목록은 사용자 토큰으로 툴 서버에서 받아 온다")
    fun tools() {
        // given
        val gateway = Gateway { session }
        val service = ConversationService(store, prompts, limiter, gateway, AgentFixtures.jsonMapper, "ops-agent", 100)

        // when
        val tools = service.tools(AgentFixtures.user(), "t-1")

        // then
        assertEquals(listOf("issue_search"), tools.map { it.name })
        assertEquals(Triple("token-7", null, "t-1"), gateway.opened.single())
        assertTrue(session.closed)
    }

    @Test
    @DisplayName("툴 서버가 꺼져 있으면 툴 목록은 UPSTREAM_FAILED 로 알린다")
    fun toolsUpstreamDown() {
        // given
        val service = ConversationService(store, prompts, limiter, Gateway { throw IllegalStateException("거부") }, AgentFixtures.jsonMapper, "ops-agent", 100)

        // when
        val error = assertThrows(BusinessException::class.java) { service.tools(AgentFixtures.user(), null) }

        // then
        assertEquals(ErrorCode.UPSTREAM_FAILED, error.code)
    }
}
