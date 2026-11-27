package com.sleekydz86.aibackoffice.operation.domain

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IssueTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)

    private fun issue() = Issue(IssueType.PLAYBACK_ERROR, Severity.HIGH, 3, "재생 멈춤", "본문", "7", now)

    @Test
    @DisplayName("접수된 이슈에 담당자를 배정하면 배정 상태가 된다")
    fun assignOpensToAssigned() {
        // given
        val issue = issue()

        // when
        issue.assign(4)

        // then
        assertEquals(IssueStatus.ASSIGNED, issue.status)
        assertEquals(4L, issue.assigneeId)
    }

    @Test
    @DisplayName("배정된 이슈는 담당자를 다시 바꿀 수 있다")
    fun reassignKeepsAssigned() {
        // given
        val issue = issue().apply { assign(4) }

        // when
        issue.assign(5)

        // then
        assertEquals(IssueStatus.ASSIGNED, issue.status)
        assertEquals(5L, issue.assigneeId)
    }

    @Test
    @DisplayName("담당자 없이 접수 상태에서 바로 해결할 수 없다")
    fun cannotResolveOpenIssue() {
        // given
        val issue = issue()

        // when
        val error = assertThrows<BusinessException> { issue.resolve("고침", now) }

        // then
        assertEquals(ErrorCode.INVALID_STATE, error.code)
        assertEquals(IssueStatus.OPEN, issue.status)
    }

    @Test
    @DisplayName("배정된 이슈를 해결하면 해결 내용과 해결 시각이 남는다")
    fun resolveAssignedIssue() {
        // given
        val issue = issue().apply { assign(4) }

        // when
        issue.resolve("다시 올렸다", now.plusHours(1))

        // then
        assertEquals(IssueStatus.RESOLVED, issue.status)
        assertEquals("다시 올렸다", issue.resolution)
        assertEquals(now.plusHours(1), issue.resolvedAt)
    }

    @Test
    @DisplayName("해결된 이슈는 다시 배정하거나 다시 해결할 수 없다")
    fun resolvedIssueIsFinal() {
        // given
        val issue = issue().apply {
            assign(4)
            resolve("고침", now)
        }

        // when
        val reassign = assertThrows<BusinessException> { issue.assign(5) }
        val resolveAgain = assertThrows<BusinessException> { issue.resolve("또 고침", now) }

        // then
        assertEquals(ErrorCode.INVALID_STATE, reassign.code)
        assertEquals(ErrorCode.INVALID_STATE, resolveAgain.code)
        assertEquals(4L, issue.assigneeId)
    }
}
