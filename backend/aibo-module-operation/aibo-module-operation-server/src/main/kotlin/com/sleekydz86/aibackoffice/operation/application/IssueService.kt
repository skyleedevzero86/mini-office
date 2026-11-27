package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.operation.domain.CommentSource
import com.sleekydz86.aibackoffice.operation.domain.Issue
import com.sleekydz86.aibackoffice.operation.domain.IssueComment
import com.sleekydz86.aibackoffice.operation.domain.IssueType
import com.sleekydz86.aibackoffice.operation.domain.Severity
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueCommentRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueSpecs
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IssueService(
    private val issues: IssueRepository,
    private val comments: IssueCommentRepository,
    private val idempotency: IdempotencyService,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun issues(user: LoginUser, filter: IssueFilter, pageable: Pageable): ListPage<IssueView> {
        SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_READ)
        val specs = buildList {
            filter.type?.let { add(IssueSpecs.type(it)) }
            filter.severity?.let { add(IssueSpecs.severity(it)) }
            filter.status?.let { add(IssueSpecs.status(it)) }
            filter.contentId?.let { add(IssueSpecs.contentId(it)) }
            filter.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { add(IssueSpecs.keyword(it.take(MAX_KEYWORD))) }
        }
        val page = issues.findAll(Specification.allOf(specs), pageable)
        return ListPage(page.content.map(IssueView::from), page.totalElements)
    }

    @Transactional(readOnly = true)
    fun issue(user: LoginUser, issueId: Long): IssueDetailView {
        SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_READ)
        val issue = requireIssue(issueId)
        val found = comments.findByIssueIdOrderByCreatedAtAscIdAsc(issueId).map(CommentView::from)
        return IssueDetailView.from(IssueView.from(issue), found)
    }

    @Transactional
    fun create(user: LoginUser, command: IssueCreateCommand, idempotencyKey: String?): IssueView =
        idempotency.execute(user, idempotencyKey, "issue.create", command, IssueView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_WRITE)
            val type = parseEnum<IssueType>(command.type, "유형 type")
            val severity = parseEnum<Severity>(command.severity, "심각도 severity")
            val title = requireText(command.title, "제목 title", MAX_TITLE)
            val description = requireText(command.description, "본문 description", MAX_TEXT)
            command.contentId?.let {
                if (it <= 0) throw BusinessException(ErrorCode.VALIDATION_FAILED, "작품 번호 contentId 는 1 이상이어야 합니다.")
            }
            val issue = issues.save(
                Issue(type, severity, command.contentId, title, description, user.effectiveUserId, LocalDateTime.now(clock)),
            )
            issues.flush()
            val view = IssueView.from(issue)
            record(user, "issue.create", view.id, null, view)
            log.info("이슈를 등록했습니다. 이슈={} 유형={} 심각도={}", view.id, type, severity)
            view
        }

    @Transactional
    fun assign(user: LoginUser, issueId: Long, command: IssueAssignCommand, idempotencyKey: String?): IssueView =
        idempotency.execute(user, idempotencyKey, "issue.assign", mapOf("issueId" to issueId, "body" to command), IssueView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_WRITE)
            val assigneeId = command.assigneeId?.takeIf { it > 0 }
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "담당자 번호 assigneeId 는 1 이상이어야 합니다.")
            val issue = requireLockedIssue(issueId)
            val before = IssueView.from(issue)
            issue.assign(assigneeId)
            issues.flush()
            val after = IssueView.from(issue)
            record(user, "issue.assign", issueId, before, after)
            log.info("이슈 담당자를 배정했습니다. 이슈={} 담당자={}", issueId, assigneeId)
            after
        }

    @Transactional
    fun comment(user: LoginUser, issueId: Long, command: IssueCommentCommand, idempotencyKey: String?): CommentView =
        idempotency.execute(user, idempotencyKey, "issue.comment", mapOf("issueId" to issueId, "body" to command), CommentView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_WRITE)
            val body = requireText(command.body, "댓글 body", MAX_TEXT)
            val issue = requireIssue(issueId)
            val source = if (user.actorType == ActorType.AGENT) CommentSource.AGENT else CommentSource.HUMAN
            val comment = comments.save(IssueComment(issueId, user.effectiveUserId, source, body, LocalDateTime.now(clock)))
            comments.flush()
            val view = CommentView.from(comment)
            record(user, "issue.comment", issueId, null, view)
            log.info("이슈에 댓글을 달았습니다. 이슈={} 작성={}", issueId, source)
            view
        }

    @Transactional
    fun resolve(user: LoginUser, issueId: Long, command: IssueResolveCommand, idempotencyKey: String?): IssueView =
        idempotency.execute(user, idempotencyKey, "issue.resolve", mapOf("issueId" to issueId, "body" to command), IssueView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_WRITE)
            val resolution = requireText(command.resolution, "해결 내용 resolution", MAX_TEXT)
            val issue = requireLockedIssue(issueId)
            val before = IssueView.from(issue)
            issue.resolve(resolution, LocalDateTime.now(clock))
            issues.flush()
            val after = IssueView.from(issue)
            record(user, "issue.resolve", issueId, before, after)
            log.info("이슈를 해결했습니다. 이슈={}", issueId)
            after
        }

    fun requireIssue(issueId: Long): Issue =
        issues.findById(issueId).orElse(null) ?: throw BusinessException(ErrorCode.NOT_FOUND, "이슈를 찾을 수 없습니다.")

    private fun requireLockedIssue(issueId: Long): Issue =
        issues.findLockedById(issueId) ?: throw BusinessException(ErrorCode.NOT_FOUND, "이슈를 찾을 수 없습니다.")

    private fun record(user: LoginUser, action: String, issueId: Long, before: Any?, after: Any?) {
        audit.record(
            user,
            action = action,
            result = AuditResult.SUCCESS,
            resourceType = RESOURCE_TYPE,
            resourceId = issueId.toString(),
            beforeValue = before,
            afterValue = after,
        )
    }

    private inline fun <reified E : Enum<E>> parseEnum(value: String?, label: String): E {
        val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$label 이 필요합니다.")
        return enumValues<E>().firstOrNull { it.name == normalized }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$label 은 ${enumValues<E>().joinToString(", ") { it.name }} 중 하나여야 합니다.")
    }

    private fun requireText(value: String?, label: String, max: Int): String {
        val text = value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "$label 이 필요합니다.")
        if (text.length > max) throw BusinessException(ErrorCode.VALIDATION_FAILED, "$label 은 ${max}자를 넘을 수 없습니다.")
        return text
    }

    companion object {
        private const val RESOURCE_TYPE = "issue"
        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 10_000
        private const val MAX_KEYWORD = 100
    }
}
