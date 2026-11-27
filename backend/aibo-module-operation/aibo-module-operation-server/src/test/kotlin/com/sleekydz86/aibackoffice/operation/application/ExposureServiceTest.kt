package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.operation.domain.ContentExposure
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ContentExposureStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IdempotencyStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import java.time.Clock
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

class ExposureServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)

    private val exposures = mock<ContentExposureStore>()
    private val issues = mock<IssueRepository>()
    private val audit = mock<AuditRecorder>()
    private val service = ExposureService(
        exposures,
        issues,
        IdempotencyService(mock<IdempotencyStore>(), JsonMapper.builder().build()),
        audit,
        clock,
    )

    private fun user(service: Boolean) = LoginUser(
        userId = if (service) "svc:mcp" else "7",
        name = "운영 담당",
        team = null,
        roles = emptySet(),
        perms = setOf(Permissions.ISSUE_READ, Permissions.EXPOSURE_WRITE),
        scopes = emptySet(),
        permissionVersion = 0,
        tokenId = null,
        tokenType = if (service) TokenType.SERVICE else TokenType.USER,
        onBehalfOf = if (service) "7" else null,
        actorType = ActorType.HUMAN,
        rawToken = "token",
    )

    private fun hidden(approvalId: Long) = ContentExposure(3, false, "권리 문제", 9, approvalId, "7", now.minusHours(1), 2)

    @Test
    @DisplayName("노출 기록이 없는 작품은 노출 중으로 돌려준다")
    fun missingRecordIsVisible() {
        // given
        whenever(exposures.find(3)).thenReturn(null)

        // when
        val view = service.exposure(user(service = false), 3)

        // then
        assertTrue(view.exposed)
        assertEquals(null, view.updatedAt)
    }

    @Test
    @DisplayName("사람 토큰으로는 승인 번호가 있어도 숨길 수 없다")
    fun humanCannotHideDirectly() {
        // when
        val error = assertThrows<BusinessException> {
            service.hide(user(service = false), 3, ExposureHideCommand(9, "권리 문제"), 55, null)
        }

        // then
        assertEquals(ErrorCode.APPROVAL_REQUIRED, error.code)
        verify(exposures, never()).findForUpdate(any())
    }

    @Test
    @DisplayName("원인 이슈 번호 없이는 숨길 수 없다")
    fun hideNeedsIssueId() {
        // when
        val error = assertThrows<BusinessException> {
            service.hide(user(service = true), 3, ExposureHideCommand(null, "권리 문제"), 55, null)
        }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
    }

    @Test
    @DisplayName("기록이 없는 작품을 숨기면 새 기록을 넣고 감사 기록을 남긴다")
    fun hideInsertsNewRecord() {
        // given
        whenever(issues.existsById(9)).thenReturn(true)
        whenever(exposures.findForUpdate(3)).thenReturn(null)

        // when
        val view = service.hide(user(service = true), 3, ExposureHideCommand(9, "권리 문제"), 55, null)

        // then
        val saved = argumentCaptor<ContentExposure>()
        verify(exposures).insert(saved.capture())
        assertFalse(saved.firstValue.exposed)
        assertEquals(9L, saved.firstValue.issueId)
        assertEquals("7", saved.firstValue.updatedBy)
        assertFalse(view.exposed)
        verify(audit).record(any(), eq("exposure.hide"), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(55L))
    }

    @Test
    @DisplayName("같은 승인 번호로 다시 숨기면 바꾸지 않고 처음 결과를 돌려준다")
    fun hideIsIdempotentByApprovalId() {
        // given
        whenever(issues.existsById(9)).thenReturn(true)
        whenever(exposures.findForUpdate(3)).thenReturn(hidden(55))

        // when
        val view = service.hide(user(service = true), 3, ExposureHideCommand(9, "권리 문제"), 55, null)

        // then
        assertFalse(view.exposed)
        verify(exposures, never()).update(any(), any())
        verify(exposures, never()).insert(any())
    }

    @Test
    @DisplayName("다른 승인으로 이미 숨긴 작품은 다시 숨길 수 없다")
    fun cannotHideTwice() {
        // given
        whenever(issues.existsById(9)).thenReturn(true)
        whenever(exposures.findForUpdate(3)).thenReturn(hidden(54))

        // when
        val error = assertThrows<BusinessException> {
            service.hide(user(service = true), 3, ExposureHideCommand(9, "권리 문제"), 55, null)
        }

        // then
        assertEquals(ErrorCode.INVALID_STATE, error.code)
    }

    @Test
    @DisplayName("숨김을 풀 때 다른 변경이 먼저 들어왔으면 409로 거절한다")
    fun restoreDetectsConcurrentChange() {
        // given
        whenever(exposures.findForUpdate(3)).thenReturn(hidden(54))
        whenever(exposures.update(any(), eq(2))).thenReturn(false)

        // when
        val error = assertThrows<BusinessException> {
            service.restore(user(service = true), 3, ExposureRestoreCommand("권리 확인 끝"), 56, null)
        }

        // then
        assertEquals(ErrorCode.CONFLICT, error.code)
    }

    @Test
    @DisplayName("숨긴 적 없는 작품은 숨김을 풀 수 없다")
    fun cannotRestoreVisibleContent() {
        // given
        whenever(exposures.findForUpdate(3)).thenReturn(null)

        // when
        val error = assertThrows<BusinessException> {
            service.restore(user(service = true), 3, ExposureRestoreCommand("확인 끝"), 56, null)
        }

        // then
        assertEquals(ErrorCode.INVALID_STATE, error.code)
    }
}
