package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.governance.domain.ToolPolicy
import com.sleekydz86.aibackoffice.governance.infrastructure.persistence.ToolPolicyStore
import java.time.Clock
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class ToolPolicyServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)
    private val store = mock<ToolPolicyStore>()
    private val audit = mock<AuditRecorder>()
    private val service = ToolPolicyService(store, GovernanceViews(JsonMapper.builder().build()), audit, clock)

    private fun admin(perms: Set<String> = setOf(Permissions.POLICY_WRITE)) =
        LoginUser("1", "관리자", null, emptySet(), perms, emptySet(), 0, null, TokenType.USER, null, ActorType.HUMAN, "token")

    private val current = ToolPolicy("issue_create", "issue", RiskLevel.WRITE_LOW, Permissions.ISSUE_WRITE, true, 0, now.minusDays(1))

    @Test
    @DisplayName("관리자는 위험도와 사용 여부를 바꾸고 바뀌기 전후를 감사 기록에 남긴다")
    fun updatesPolicy() {
        // given
        whenever(store.findForUpdate("issue_create")).thenReturn(current)

        // when
        val view = service.update(admin(), "issue_create", ToolPolicyUpdateCommand(riskLevel = "write_high", enabled = false, approvalTtlMinutes = 60))

        // then
        val saved = argumentCaptor<ToolPolicy>()
        verify(store).update(saved.capture())
        assertEquals(RiskLevel.WRITE_HIGH, saved.firstValue.riskLevel)
        assertEquals(false, saved.firstValue.enabled)
        assertEquals(60, saved.firstValue.approvalTtlMinutes)
        assertEquals(Permissions.ISSUE_WRITE, saved.firstValue.requiredPermission)
        assertEquals("WRITE_HIGH", view.riskLevel)
        verify(audit).record(any(), eq("tool_policy.update"), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @DisplayName("없는 권한 코드나 범위를 벗어난 대기 시간은 거절한다")
    fun rejectsInvalidValues() {
        // given
        whenever(store.findForUpdate("issue_create")).thenReturn(current)

        // when
        val badPermission = assertThrows<BusinessException> {
            service.update(admin(), "issue_create", ToolPolicyUpdateCommand(requiredPermission = "issue:delete"))
        }
        val badTtl = assertThrows<BusinessException> {
            service.update(admin(), "issue_create", ToolPolicyUpdateCommand(approvalTtlMinutes = 20_000))
        }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, badPermission.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, badTtl.code)
        verify(store, never()).update(any())
    }

    @Test
    @DisplayName("policy:write 권한이 없으면 서비스 안에서도 막는다")
    fun needsPolicyWrite() {
        // when
        val error = assertThrows<BusinessException> {
            service.update(admin(setOf(Permissions.AUDIT_READ)), "issue_create", ToolPolicyUpdateCommand(enabled = false))
        }

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }
}
