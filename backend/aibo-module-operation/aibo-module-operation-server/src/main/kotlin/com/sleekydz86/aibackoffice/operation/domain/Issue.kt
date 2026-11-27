package com.sleekydz86.aibackoffice.operation.domain

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class IssueType { PLAYBACK_ERROR, META_ERROR, RIGHTS }

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

enum class IssueStatus { OPEN, ASSIGNED, RESOLVED }

enum class CommentSource { HUMAN, AGENT }

@Entity
@Table(name = "issues")
class Issue(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: IssueType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var severity: Severity,
    @Column(name = "content_id")
    var contentId: Long?,
    @Column(nullable = false, length = 200)
    var title: String,
    @Column(nullable = false, columnDefinition = "text")
    var description: String,
    @Column(nullable = false, length = 100)
    var reporter: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var status: IssueStatus = IssueStatus.OPEN

    @Column(name = "assignee_id")
    var assigneeId: Long? = null

    @Column(columnDefinition = "text")
    var resolution: String? = null

    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime? = null

    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 이슈입니다.")

    fun assign(assigneeId: Long) {
        if (status == IssueStatus.RESOLVED) {
            throw BusinessException(ErrorCode.INVALID_STATE, "해결된 이슈는 담당자를 바꿀 수 없습니다.")
        }
        this.assigneeId = assigneeId
        status = IssueStatus.ASSIGNED
    }

    fun resolve(resolution: String, at: LocalDateTime) {
        when (status) {
            IssueStatus.OPEN -> throw BusinessException(ErrorCode.INVALID_STATE, "담당자를 먼저 배정해야 해결할 수 있습니다.")
            IssueStatus.RESOLVED -> throw BusinessException(ErrorCode.INVALID_STATE, "이미 해결된 이슈입니다.")
            IssueStatus.ASSIGNED -> Unit
        }
        this.resolution = resolution
        resolvedAt = at
        status = IssueStatus.RESOLVED
    }
}

@Entity
@Table(name = "issue_comments")
class IssueComment(
    @Column(name = "issue_id", nullable = false)
    var issueId: Long,
    @Column(nullable = false, length = 100)
    var author: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var source: CommentSource,
    @Column(nullable = false, columnDefinition = "text")
    var body: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 댓글입니다.")
}
