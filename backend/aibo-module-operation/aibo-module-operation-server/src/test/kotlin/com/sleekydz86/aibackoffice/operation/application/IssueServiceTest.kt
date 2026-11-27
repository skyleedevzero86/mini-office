package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.operation.domain.CommentSource
import com.sleekydz86.aibackoffice.operation.domain.Issue
import com.sleekydz86.aibackoffice.operation.domain.IssueComment
import com.sleekydz86.aibackoffice.operation.domain.IssueStatus
import com.sleekydz86.aibackoffice.operation.domain.IssueType
import com.sleekydz86.aibackoffice.operation.domain.Severity
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IdempotencyStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueCommentRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper

class IssueServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)

    private val issues = mock<IssueRepository>()
    private val comments = mock<IssueCommentRepository>()
    private val audit = mock<AuditRecorder>()
    private val service = IssueService(
        issues,
        comments,
        IdempotencyService(mock<IdempotencyStore>(), JsonMapper.builder().build()),
        audit,
        clock,
    )

    private fun user(actorType: ActorType = ActorType.HUMAN, perms: Set<String> = setOf(Permissions.ISSUE_READ, Permissions.ISSUE_WRITE)) =
        LoginUser(
            userId = "7",
            name = "운영 담당",
            team = null,
            roles = emptySet(),
            perms = perms,
            scopes = emptySet(),
            permissionVersion = 0,
            tokenId = null,
            tokenType = TokenType.USER,
            onBehalfOf = null,
            actorType = actorType,
            rawToken = "token",
        )

    private fun stored() = Issue(IssueType.RIGHTS, Severity.HIGH, 3, "권리 확인", "본문", "2", now.minusDays(1)).apply { id = 9 }

    @Test
    @DisplayName("이슈를 등록하면 접수 상태로 저장하고 등록한 사람을 남긴다")
    fun createsOpenIssue() {
        // given
        whenever(issues.save(any<Issue>())).thenAnswer { (it.arguments[0] as Issue).apply { id = 11 } }

        // when
        val view = service.create(user(), IssueCreateCommand("playback_error", "critical", 1, " 재생 멈춤 ", "12분에서 멈춤"), null)

        // then
        assertEquals(11L, view.id)
        assertEquals(IssueStatus.OPEN, view.status)
        assertEquals(IssueType.PLAYBACK_ERROR, view.type)
        assertEquals(Severity.CRITICAL, view.severity)
        assertEquals("재생 멈춤", view.title)
        assertEquals("7", view.reporter)
        verify(audit).record(any(), eq("issue.create"), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @DisplayName("모르는 유형이나 빈 제목은 400으로 거절한다")
    fun rejectsInvalidCreate() {
        // when
        val badType = assertThrows<BusinessException> {
            service.create(user(), IssueCreateCommand("UNKNOWN", "LOW", null, "제목", "본문"), null)
        }
        val blankTitle = assertThrows<BusinessException> {
            service.create(user(), IssueCreateCommand("RIGHTS", "LOW", null, "  ", "본문"), null)
        }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, badType.code)
        assertEquals(ErrorCode.VALIDATION_FAILED, blankTitle.code)
        verify(issues, never()).save(any<Issue>())
    }

    @Test
    @DisplayName("issue:write 권한이 없으면 서비스 안에서도 막는다")
    fun createChecksPermissionInService() {
        // when
        val error = assertThrows<BusinessException> {
            service.create(user(perms = setOf(Permissions.ISSUE_READ)), IssueCreateCommand("RIGHTS", "LOW", null, "제목", "본문"), null)
        }

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }

    @Test
    @DisplayName("에이전트를 거쳐 온 댓글은 AGENT로 남긴다")
    fun agentCommentIsMarked() {
        // given
        whenever(issues.findById(9)).thenReturn(Optional.of(stored()))
        whenever(comments.save(any<IssueComment>())).thenAnswer { (it.arguments[0] as IssueComment).apply { id = 21 } }

        // when
        val view = service.comment(user(actorType = ActorType.AGENT), 9, IssueCommentCommand("재현 경로를 정리했습니다"), null)

        // then
        assertEquals(CommentSource.AGENT, view.source)
        assertEquals("7", view.author)
    }

    @Test
    @DisplayName("사람이 쓴 댓글은 HUMAN으로 남긴다")
    fun humanCommentIsMarked() {
        // given
        whenever(issues.findById(9)).thenReturn(Optional.of(stored()))
        whenever(comments.save(any<IssueComment>())).thenAnswer { (it.arguments[0] as IssueComment).apply { id = 22 } }

        // when
        val view = service.comment(user(), 9, IssueCommentCommand("확인했습니다"), null)

        // then
        assertEquals(CommentSource.HUMAN, view.source)
    }

    @Test
    @DisplayName("담당자를 배정하지 않은 이슈는 해결할 수 없다")
    fun cannotResolveWithoutAssignee() {
        // given
        whenever(issues.findLockedById(9)).thenReturn(stored())

        // when
        val error = assertThrows<BusinessException> { service.resolve(user(), 9, IssueResolveCommand("고침"), null) }

        // then
        assertEquals(ErrorCode.INVALID_STATE, error.code)
        verify(audit, never()).record(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @DisplayName("배정과 해결을 차례로 하면 해결 상태가 되고 시각이 남는다")
    fun assignThenResolve() {
        // given
        val issue = stored()
        whenever(issues.findLockedById(9)).thenReturn(issue)

        // when
        service.assign(user(), 9, IssueAssignCommand(4), null)
        val view = service.resolve(user(), 9, IssueResolveCommand("계약 연장 확인"), null)

        // then
        assertEquals(IssueStatus.RESOLVED, view.status)
        assertEquals(4L, view.assigneeId)
        assertEquals(now.atZone(Zones.SEOUL).toOffsetDateTime(), view.resolvedAt)
    }

    @Test
    @DisplayName("없는 이슈는 404로 알려 준다")
    fun missingIssue() {
        // given
        whenever(issues.findById(99)).thenReturn(Optional.empty())

        // when
        val error = assertThrows<BusinessException> { service.issue(user(), 99) }

        // then
        assertEquals(ErrorCode.NOT_FOUND, error.code)
    }
}
