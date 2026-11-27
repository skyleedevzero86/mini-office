package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.operation.domain.CommentSource
import com.sleekydz86.aibackoffice.operation.domain.ContentExposure
import com.sleekydz86.aibackoffice.operation.domain.Issue
import com.sleekydz86.aibackoffice.operation.domain.IssueComment
import com.sleekydz86.aibackoffice.operation.domain.IssueStatus
import com.sleekydz86.aibackoffice.operation.domain.IssueType
import com.sleekydz86.aibackoffice.operation.domain.Severity
import java.time.OffsetDateTime

data class IssueView(
    val id: Long,
    val type: IssueType,
    val severity: Severity,
    val status: IssueStatus,
    val contentId: Long?,
    val title: String,
    val description: String,
    val reporter: String,
    val assigneeId: Long?,
    val resolution: String?,
    val createdAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?,
) {
    companion object {
        fun from(issue: Issue): IssueView = IssueView(
            id = issue.requireId(),
            type = issue.type,
            severity = issue.severity,
            status = issue.status,
            contentId = issue.contentId,
            title = issue.title,
            description = issue.description,
            reporter = issue.reporter,
            assigneeId = issue.assigneeId,
            resolution = issue.resolution,
            createdAt = SeoulTime.toOffset(issue.createdAt),
            resolvedAt = issue.resolvedAt?.let(SeoulTime::toOffset),
        )
    }
}

data class CommentView(
    val id: Long,
    val author: String,
    val source: CommentSource,
    val body: String,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(comment: IssueComment): CommentView = CommentView(
            id = comment.requireId(),
            author = comment.author,
            source = comment.source,
            body = comment.body,
            createdAt = SeoulTime.toOffset(comment.createdAt),
        )
    }
}

data class IssueDetailView(
    val id: Long,
    val type: IssueType,
    val severity: Severity,
    val status: IssueStatus,
    val contentId: Long?,
    val title: String,
    val description: String,
    val reporter: String,
    val assigneeId: Long?,
    val resolution: String?,
    val createdAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?,
    val comments: List<CommentView>,
) {
    companion object {
        fun from(issue: IssueView, comments: List<CommentView>): IssueDetailView = IssueDetailView(
            id = issue.id,
            type = issue.type,
            severity = issue.severity,
            status = issue.status,
            contentId = issue.contentId,
            title = issue.title,
            description = issue.description,
            reporter = issue.reporter,
            assigneeId = issue.assigneeId,
            resolution = issue.resolution,
            createdAt = issue.createdAt,
            resolvedAt = issue.resolvedAt,
            comments = comments,
        )
    }
}

data class ExposureView(
    val contentId: Long,
    val exposed: Boolean,
    val reason: String?,
    val issueId: Long?,
    val approvalId: Long?,
    val updatedBy: String?,
    val updatedAt: OffsetDateTime?,
) {
    companion object {
        fun from(exposure: ContentExposure): ExposureView = ExposureView(
            contentId = exposure.contentId,
            exposed = exposure.exposed,
            reason = exposure.reason,
            issueId = exposure.issueId,
            approvalId = exposure.approvalId,
            updatedBy = exposure.updatedBy,
            updatedAt = exposure.updatedAt?.let(SeoulTime::toOffset),
        )
    }
}

data class SummaryView(
    val todaySlots: Long,
    val draftSlots: Long,
    val openIssuesBySeverity: Map<Severity, Long>,
    val hiddenContents: Long,
)

data class IssueCreateCommand(
    val type: String? = null,
    val severity: String? = null,
    val contentId: Long? = null,
    val title: String? = null,
    val description: String? = null,
)

data class IssueAssignCommand(val assigneeId: Long? = null)

data class IssueCommentCommand(val body: String? = null)

data class IssueResolveCommand(val resolution: String? = null)

data class ExposureHideCommand(val issueId: Long? = null, val reason: String? = null)

data class ExposureRestoreCommand(val reason: String? = null)

data class IssueFilter(
    val type: IssueType? = null,
    val severity: Severity? = null,
    val status: IssueStatus? = null,
    val contentId: Long? = null,
    val keyword: String? = null,
)

data class ListPage<T>(val items: List<T>, val total: Long)
