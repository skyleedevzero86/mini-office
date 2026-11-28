package com.sleekydz86.aibackoffice.mcp.infrastructure.tool

import com.sleekydz86.aibackoffice.mcp.application.ToolCatalog
import com.sleekydz86.aibackoffice.mcp.application.tool.McpToolHandler
import com.sleekydz86.aibackoffice.mcp.infrastructure.client.DownstreamClients
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolCatalogContractTest {

    private val clients = mock<DownstreamClients>()

    private val handlers: List<McpToolHandler> = listOf(
        ContentSearchTitlesTool(clients),
        ContentGetTitleTool(clients),
        ContentFindByExternalIdTool(clients),
        ContentSearchTmdbCandidatesTool(clients),
        ContentLinkExternalIdTool(clients),
        ContentUpdateLocalizationTool(clients),
        ContentApplyTmdbTool(clients),
        ContentGetQualityReportTool(clients),
        ContentGetMigrationStatusTool(clients),
        ScheduleListSlotsTool(clients),
        ScheduleFindConflictsTool(clients),
        ScheduleCreateDraftTool(clients),
        SchedulePublishSlotTool(clients),
        ScheduleCancelSlotTool(clients),
        IssueSearchTool(clients),
        IssueGetTool(clients),
        IssueCreateTool(clients),
        IssueAssignTool(clients),
        IssueAddCommentTool(clients),
        IssueResolveTool(clients),
        ExposureHideContentTool(clients),
        ExposureRestoreContentTool(clients),
        ApprovalListPendingTool(clients),
        ApprovalGetTool(clients),
        AuditSearchTool(clients),
        AutomationListCandidatesTool(clients),
    )

    private fun seededPolicies(): Map<String, String> {
        val seed = Path.of("../../aibo-module-governance/aibo-module-governance-server/src/main/resources/db/migration/V2__seed.sql")
        val row = Regex("""\('([a-z_]+)',\s*'([a-z]+)',\s*'([A-Z_]+)'""")
        return row.findAll(Files.readString(seed)).associate { it.groupValues[1] to it.groupValues[3] }
    }

    @Test
    @DisplayName("등록한 툴 26개 이름이 governance 툴 정책 기준 데이터와 똑같다")
    fun namesMatchPolicySeed() {
        // given
        val seeded = seededPolicies()

        // when
        val names = ToolCatalog(handlers).all().map { it.definition.name }.toSet()

        // then
        assertEquals(26, names.size)
        assertEquals(seeded.keys, names)
    }

    @Test
    @DisplayName("승인이 필요한 툴은 정확히 다섯 개이고 모두 승인 요청 이유를 받을 수 있다")
    fun approvalToolsAcceptReason() {
        // given
        val high = seededPolicies().filterValues { it == "WRITE_HIGH" }.keys

        // when
        val reasonless = handlers.filter { it.definition.name in high }.filterNot { handler ->
            @Suppress("UNCHECKED_CAST")
            val properties = handler.definition.inputSchema["properties"] as Map<String, Any?>
            "reason" in properties
        }

        // then
        assertEquals(setOf("content_link_external_id", "schedule_publish_slot", "schedule_cancel_slot", "exposure_hide_content", "exposure_restore_content"), high)
        assertTrue(reasonless.isEmpty(), "이유 칸이 없는 승인 툴: ${reasonless.map { it.definition.name }}")
    }

    @Test
    @DisplayName("모든 툴은 설명과 객체 입력 형식을 가진다")
    fun everyToolDescribed() {
        // when
        val broken = handlers.filter { it.definition.description.isBlank() || it.definition.inputSchema["type"] != "object" }

        // then
        assertTrue(broken.isEmpty())
    }
}
