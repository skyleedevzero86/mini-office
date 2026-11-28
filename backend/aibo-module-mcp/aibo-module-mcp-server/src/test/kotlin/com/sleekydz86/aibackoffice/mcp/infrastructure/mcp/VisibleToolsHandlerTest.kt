package com.sleekydz86.aibackoffice.mcp.infrastructure.mcp

import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.mcp.application.ToolCatalog
import com.sleekydz86.aibackoffice.mcp.application.ToolVisibilityService
import com.sleekydz86.aibackoffice.mcp.support.McpFixtures
import com.sleekydz86.aibackoffice.mcp.support.McpFixtures.FakeTool
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.server.McpStatelessServerHandler
import io.modelcontextprotocol.spec.McpSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import reactor.core.publisher.Mono

class VisibleToolsHandlerTest {

    private val visibility = ToolVisibilityService(
        ToolCatalog(listOf(FakeTool("schedule_list_slots"), FakeTool("audit_search"))),
        McpFixtures.policies(
            McpFixtures.policy("schedule_list_slots", RiskLevel.READ, Permissions.SCHEDULE_READ),
            McpFixtures.policy("audit_search", RiskLevel.READ, Permissions.AUDIT_READ, domain = "governance"),
        ),
    )
    private val delegateResponse = McpSchema.JSONRPCResponse.result("2", mapOf("ok" to true))
    private val delegate = mock<McpStatelessServerHandler> {
        on { handleRequest(any(), any()) } doReturn Mono.just(delegateResponse)
    }
    private val handler = VisibleToolsHandler(delegate, visibility)

    @Test
    @DisplayName("tools/list 는 그 사용자에게 보이는 툴만 돌려준다")
    fun listsOnlyVisibleTools() {
        // given
        val user = McpFixtures.user(perms = setOf(Permissions.SCHEDULE_READ))
        val context = McpTransportContext.create(mapOf(McpRequestContext.USER to user))

        // when
        val response = handler.handleRequest(context, McpSchema.JSONRPCRequest(McpSchema.METHOD_TOOLS_LIST, "1", null)).block()!!

        // then
        val tools = (response.result() as McpSchema.ListToolsResult).tools().map { it.name() }
        assertEquals(listOf("schedule_list_slots"), tools)
        verify(delegate, never()).handleRequest(any(), any())
    }

    @Test
    @DisplayName("로그인 정보가 없으면 tools/list 를 오류로 돌려준다")
    fun listWithoutUserFails() {
        // when
        val response = handler.handleRequest(McpTransportContext.EMPTY, McpSchema.JSONRPCRequest(McpSchema.METHOD_TOOLS_LIST, "1", null)).block()!!

        // then
        assertNotNull(response.error())
    }

    @Test
    @DisplayName("tools/list 가 아닌 요청은 원래 처리기로 넘긴다")
    fun delegatesOtherMethods() {
        // given
        val request = McpSchema.JSONRPCRequest(McpSchema.METHOD_TOOLS_CALL, "2", mapOf("name" to "schedule_list_slots"))

        // when
        val response = handler.handleRequest(McpTransportContext.EMPTY, request).block()

        // then
        assertSame(delegateResponse, response)
    }
}
