package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.governance.api.ApprovalExecuteRequest
import com.sleekydz86.aibackoffice.mcp.support.McpFixtures
import com.sleekydz86.aibackoffice.mcp.support.McpFixtures.FakeTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ApprovedExecutionServiceTest {

    private val publishTool = FakeTool("schedule_publish_slot", channelArgument = true)
    private val listTool = FakeTool("schedule_list_slots")
    private val policies = McpFixtures.policies(
        McpFixtures.policy("schedule_publish_slot", RiskLevel.WRITE_HIGH, Permissions.SCHEDULE_PUBLISH),
        McpFixtures.policy("schedule_list_slots", RiskLevel.READ, Permissions.SCHEDULE_READ),
    )
    private val ledger = MemoryLedger()
    private val auditor = mock<ToolAuditor>()
    private val service = ApprovedExecutionService(
        ToolCatalog(listOf(publishTool, listTool)),
        policies,
        ledger,
        auditor,
        McpFixtures.jsonMapper,
    )

    private val arguments = mapOf<String, Any?>("slotId" to 10, "channelCode" to "TVN")
    private val request = ApprovalExecuteRequest(55, "schedule_publish_slot", arguments, "approval:55")
    private val requester = McpFixtures.delegated(perms = setOf(Permissions.SCHEDULE_PUBLISH), scopes = setOf("TVN"))

    class MemoryLedger : ExecutionLedger {
        val entries = mutableMapOf<Long, String>()
        var broken = false

        override fun begin(approvalId: Long): LedgerEntry {
            if (broken) throw IllegalStateException("Redis 연결 실패")
            val current = entries[approvalId] ?: run {
                entries[approvalId] = "RUNNING"
                return LedgerEntry.Started
            }
            return if (current == "RUNNING") LedgerEntry.Running else LedgerEntry.Done(current)
        }

        override fun finish(approvalId: Long, resultJson: String) {
            entries[approvalId] = resultJson
        }
    }

    private fun auditedResult(): ToolResult {
        val captor = argumentCaptor<ToolResult>()
        verify(auditor).record(any(), eq(ToolAuditor.EXECUTE_ACTION), any(), any(), captor.capture(), eq(55L))
        return captor.firstValue
    }

    @Test
    @DisplayName("승인된 요청은 승인 번호와 멱등 키를 붙여 실행한다")
    fun executesApproved() {
        // when
        val result = service.execute(requester, 55, request)

        // then
        assertTrue(result.ok)
        assertEquals(55L, result.approvalId)
        val call = publishTool.calls.single()
        assertEquals(55L, call.approvalId)
        assertEquals("approval:55", call.idempotencyKey)
        assertEquals(1, publishTool.prechecks.size)
        assertEquals(AuditResult.SUCCESS, ToolFailures.auditResult(auditedResult()))
    }

    @Test
    @DisplayName("같은 승인 번호로 두 번 와도 한 번만 실행하고 처음 결과를 돌려준다")
    fun executesOnlyOnce() {
        // given
        val first = service.execute(requester, 55, request)

        // when
        val second = service.execute(requester, 55, request)

        // then
        assertEquals(1, publishTool.calls.size)
        assertEquals(first, second)
    }

    @Test
    @DisplayName("다른 실행이 아직 끝나지 않았으면 실행하지 않는다")
    fun runningIsRejected() {
        // given
        ledger.entries[55] = "RUNNING"

        // when
        val result = service.execute(requester, 55, request)

        // then
        assertEquals(ErrorCode.INVALID_STATE.name, result.code)
        assertTrue(publishTool.calls.isEmpty())
    }

    @Test
    @DisplayName("승인 사이에 권한을 잃었으면 실행 직전에 거절한다")
    fun revokedPermissionFails() {
        // given
        val revoked = McpFixtures.delegated(perms = setOf(Permissions.SCHEDULE_READ))

        // when
        val result = service.execute(revoked, 55, request)

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED.name, result.code)
        assertTrue(publishTool.calls.isEmpty())
        assertEquals(AuditResult.DENIED, ToolFailures.auditResult(auditedResult()))
    }

    @Test
    @DisplayName("승인 사이에 담당 채널이 빠졌으면 실행 직전에 거절한다")
    fun revokedChannelFails() {
        // given
        val moved = McpFixtures.delegated(perms = setOf(Permissions.SCHEDULE_PUBLISH), scopes = setOf("OCN"))

        // when
        val result = service.execute(moved, 55, request)

        // then
        assertEquals(ErrorCode.SCOPE_DENIED.name, result.code)
        assertTrue(publishTool.calls.isEmpty())
    }

    @Test
    @DisplayName("사용자 토큰, 다른 승인 번호, 다른 멱등 키로는 실행하지 않는다")
    fun rejectsWrongCaller() {
        // given
        val human = McpFixtures.user(perms = setOf(Permissions.SCHEDULE_PUBLISH))

        // when
        val byUser = service.execute(human, 55, request)
        val otherId = service.execute(requester, 56, request)
        val otherKey = service.execute(requester, 55, request.copy(idempotencyKey = "아무 키"))

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED.name, byUser.code)
        assertEquals(ErrorCode.VALIDATION_FAILED.name, otherId.code)
        assertEquals(ErrorCode.VALIDATION_FAILED.name, otherKey.code)
        assertTrue(publishTool.calls.isEmpty())
        assertTrue(ledger.entries.isEmpty())
    }

    @Test
    @DisplayName("승인이 필요 없는 툴은 이 주소로 실행하지 않는다")
    fun rejectsReadTool() {
        // when
        val result = service.execute(requester, 55, request.copy(toolName = "schedule_list_slots"))

        // then
        assertEquals(ErrorCode.INVALID_STATE.name, result.code)
        assertTrue(listTool.calls.isEmpty())
    }

    @Test
    @DisplayName("중복 실행을 막는 저장소에 닿지 못하면 실행하지 않는다")
    fun ledgerDownDoesNotExecute() {
        // given
        ledger.broken = true

        // when
        val result = service.execute(requester, 55, request)

        // then
        assertEquals(ErrorCode.UPSTREAM_FAILED.name, result.code)
        assertTrue(publishTool.calls.isEmpty())
        assertEquals(AuditResult.FAILED, ToolFailures.auditResult(auditedResult()))
    }
}
