package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.operation.domain.ContentExposure
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.ContentExposureStore
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExposureService(
    private val exposures: ContentExposureStore,
    private val issues: IssueRepository,
    private val idempotency: IdempotencyService,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun exposures(user: LoginUser, exposed: Boolean?, query: ListQuery): ListPage<ExposureView> {
        SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_READ)
        val items = exposures.list(exposed, query.offset, query.limit, query.sort ?: "updatedAt", query.ascending)
        return ListPage(items.map(ExposureView::from), exposures.count(exposed))
    }

    @Transactional(readOnly = true)
    fun exposure(user: LoginUser, contentId: Long): ExposureView {
        SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_READ)
        requireContentId(contentId)
        return ExposureView.from(exposures.find(contentId) ?: ContentExposure.visible(contentId))
    }

    @Transactional
    fun hide(user: LoginUser, contentId: Long, command: ExposureHideCommand, approvalId: Long?, idempotencyKey: String?): ExposureView =
        idempotency.execute(user, idempotencyKey, "exposure.hide", mapOf("contentId" to contentId, "body" to command, "approvalId" to approvalId), ExposureView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.EXPOSURE_WRITE)
            val approved = ApprovedExecution.require(user, approvalId, "작품 숨김은 승인을 거친 실행으로만 할 수 있습니다.")
            requireContentId(contentId)
            val issueId = command.issueId?.takeIf { it > 0 }
                ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "숨기려면 원인이 된 이슈 번호 issueId 가 필요합니다.")
            val reason = requireReason(command.reason)
            if (!issues.existsById(issueId)) throw BusinessException(ErrorCode.NOT_FOUND, "이슈를 찾을 수 없습니다.")
            val current = exposures.findForUpdate(contentId)
            if (current != null && current.alreadyDone(false, approved)) {
                log.info("같은 승인 번호로 이미 숨긴 작품이라 그대로 돌려줍니다. 작품={} 승인={}", contentId, approved)
                return@execute ExposureView.from(current)
            }
            val before = current ?: ContentExposure.visible(contentId)
            val after = before.hide(issueId, reason, approved, user.effectiveUserId, LocalDateTime.now(clock))
            save(current, after)
            record(user, "exposure.hide", contentId, before, after, approved)
            log.info("작품을 숨겼습니다. 작품={} 이슈={} 승인={}", contentId, issueId, approved)
            ExposureView.from(after)
        }

    @Transactional
    fun restore(user: LoginUser, contentId: Long, command: ExposureRestoreCommand, approvalId: Long?, idempotencyKey: String?): ExposureView =
        idempotency.execute(user, idempotencyKey, "exposure.restore", mapOf("contentId" to contentId, "body" to command, "approvalId" to approvalId), ExposureView::class.java) {
            SecurityFrameworkUtils.requirePermission(user, Permissions.EXPOSURE_WRITE)
            val approved = ApprovedExecution.require(user, approvalId, "작품 숨김 해제는 승인을 거친 실행으로만 할 수 있습니다.")
            requireContentId(contentId)
            val reason = requireReason(command.reason)
            val current = exposures.findForUpdate(contentId)
                ?: throw BusinessException(ErrorCode.INVALID_STATE, "숨김 처리되지 않은 작품입니다.")
            if (current.alreadyDone(true, approved)) {
                log.info("같은 승인 번호로 이미 숨김을 푼 작품이라 그대로 돌려줍니다. 작품={} 승인={}", contentId, approved)
                return@execute ExposureView.from(current)
            }
            val after = current.restore(reason, approved, user.effectiveUserId, LocalDateTime.now(clock))
            save(current, after)
            record(user, "exposure.restore", contentId, current, after, approved)
            log.info("작품 숨김을 풀었습니다. 작품={} 승인={}", contentId, approved)
            ExposureView.from(after)
        }

    private fun save(current: ContentExposure?, next: ContentExposure) {
        if (current == null) {
            exposures.insert(next)
            return
        }
        if (!exposures.update(next, current.version)) {
            throw BusinessException(ErrorCode.CONFLICT, "다른 사람이 먼저 노출 상태를 바꿨습니다. 새로 불러온 뒤 다시 시도하세요.")
        }
    }

    private fun record(user: LoginUser, action: String, contentId: Long, before: ContentExposure, after: ContentExposure, approvalId: Long) {
        audit.record(
            user,
            action = action,
            result = AuditResult.SUCCESS,
            resourceType = RESOURCE_TYPE,
            resourceId = contentId.toString(),
            beforeValue = ExposureView.from(before),
            afterValue = ExposureView.from(after),
            approvalId = approvalId,
        )
    }

    private fun requireContentId(contentId: Long) {
        if (contentId <= 0) throw BusinessException(ErrorCode.VALIDATION_FAILED, "작품 번호는 1 이상이어야 합니다.")
    }

    private fun requireReason(value: String?): String {
        val reason = value?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "바꾸는 이유 reason 이 필요합니다.")
        if (reason.length > MAX_REASON) throw BusinessException(ErrorCode.VALIDATION_FAILED, "바꾸는 이유는 ${MAX_REASON}자를 넘을 수 없습니다.")
        return reason
    }

    companion object {
        private const val RESOURCE_TYPE = "content_exposure"
        private const val MAX_REASON = 500
    }
}
