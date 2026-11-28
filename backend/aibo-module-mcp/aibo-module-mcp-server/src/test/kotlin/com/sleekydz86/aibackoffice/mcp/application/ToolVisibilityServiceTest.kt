package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.mcp.domain.AccessDecision
import com.sleekydz86.aibackoffice.mcp.domain.ToolAccess
import com.sleekydz86.aibackoffice.mcp.support.McpFixtures
import com.sleekydz86.aibackoffice.mcp.support.McpFixtures.FakeTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ToolVisibilityServiceTest {

    private val catalog = ToolCatalog(
        listOf(
            FakeTool("schedule_list_slots"),
            FakeTool("schedule_publish_slot"),
            FakeTool("issue_search"),
            FakeTool("approval_list_pending"),
            FakeTool("content_get_title"),
        ),
    )
    private val policies = McpFixtures.policies(
        McpFixtures.policy("schedule_list_slots", RiskLevel.READ, Permissions.SCHEDULE_READ),
        McpFixtures.policy("schedule_publish_slot", RiskLevel.WRITE_HIGH, Permissions.SCHEDULE_PUBLISH),
        McpFixtures.policy("issue_search", RiskLevel.READ, Permissions.ISSUE_READ, enabled = false),
        McpFixtures.policy("approval_list_pending", RiskLevel.READ, Permissions.APPROVAL_READ_SELF, domain = "governance"),
        McpFixtures.policy("audit_search", RiskLevel.READ, Permissions.AUDIT_READ, domain = "governance"),
    )
    private val service = ToolVisibilityService(catalog, policies)

    @Test
    @DisplayName("권한이 없는 툴, 꺼진 툴, 정책이 없는 툴, 처리기가 없는 툴은 목록에서 아예 빠진다")
    fun hidesToolsWithoutPermission() {
        // given
        val user = McpFixtures.user(perms = setOf(Permissions.SCHEDULE_READ, Permissions.ISSUE_READ, Permissions.AUDIT_READ))

        // when
        val names = service.visible(user).map { it.name }

        // then
        assertEquals(listOf("approval_list_pending", "schedule_list_slots"), names)
    }

    @Test
    @DisplayName("게시 권한이 있으면 승인이 필요한 툴로 표시해 보여 준다")
    fun showsApprovalTool() {
        // given
        val user = McpFixtures.user(perms = setOf(Permissions.SCHEDULE_PUBLISH))

        // when
        val publish = service.visible(user).single { it.name == "schedule_publish_slot" }

        // then
        assertTrue(publish.needsApproval)
        assertEquals("WRITE_HIGH", publish.riskLevel)
    }

    @Test
    @DisplayName("서비스 토큰에는 툴 목록을 보여 주지 않는다")
    fun serviceTokenSeesNothing() {
        // given
        val service = McpFixtures.delegated(perms = Permissions.ALL.toSet())

        // when
        val tools = this.service.visible(service)

        // then
        assertTrue(tools.isEmpty())
    }

    @Test
    @DisplayName("승인과 반려 툴은 권한이 있어도 막고 등록조차 할 수 없다")
    fun approveToolIsNeverExposed() {
        // given
        val user = McpFixtures.user(perms = setOf(Permissions.APPROVAL_DECIDE))
        val policy = McpFixtures.policy("approval_approve", RiskLevel.WRITE_HIGH, Permissions.APPROVAL_DECIDE)

        // when
        val decision = ToolAccess.decide(user, "approval_approve", true, policy)

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED, (decision as AccessDecision.Denied).code)
        assertThrows<IllegalArgumentException> { ToolCatalog(listOf(FakeTool("approval_approve"))) }
    }
}
