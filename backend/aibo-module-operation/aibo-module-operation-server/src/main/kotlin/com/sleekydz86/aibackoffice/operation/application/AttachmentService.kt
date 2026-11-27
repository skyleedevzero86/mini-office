package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.file.core.FileRules
import com.sleekydz86.aibackoffice.framework.file.core.FileStorage
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.operation.domain.IssueAttachment
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueAttachmentRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import java.io.InputStream
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

data class AttachmentUpload(
    val fileName: String?,
    val contentType: String?,
    val sizeBytes: Long,
    val open: () -> InputStream,
)

data class AttachmentView(
    val id: Long,
    val issueId: Long,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedBy: String,
    val createdAt: OffsetDateTime,
    val downloadUrl: String?,
    val downloadExpiresAt: OffsetDateTime?,
)

@Service
class AttachmentService(
    private val issues: IssueRepository,
    private val attachments: IssueAttachmentRepository,
    private val storage: ObjectProvider<FileStorage>,
    private val rules: FileRules,
    private val audit: AuditRecorder,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upload(user: LoginUser, issueId: Long, upload: AttachmentUpload): AttachmentView {
        SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_WRITE)
        val files = requireStorage()
        if (!issues.existsById(issueId)) throw BusinessException(ErrorCode.NOT_FOUND, "이슈를 찾을 수 없습니다.")
        if (attachments.countByIssueId(issueId) >= MAX_PER_ISSUE) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "이슈 하나에 첨부는 ${MAX_PER_ISSUE}개까지 올릴 수 있습니다.")
        }
        val checked = rules.check(upload.fileName, upload.contentType, upload.sizeBytes)
        val key = rules.key("issues/$issueId", checked.fileName)
        upload.open().use { files.put(key, checked.contentType, checked.sizeBytes, it) }
        try {
            val view = transactions.execute {
                val saved = attachments.save(
                    IssueAttachment(issueId, key, checked.fileName, checked.contentType, checked.sizeBytes, user.effectiveUserId, LocalDateTime.now(clock)),
                )
                attachments.flush()
                val result = toView(saved, null)
                audit.record(
                    user,
                    action = "issue.attachment.upload",
                    result = AuditResult.SUCCESS,
                    resourceType = RESOURCE_TYPE,
                    resourceId = issueId.toString(),
                    afterValue = result,
                )
                result
            } ?: throw BusinessException(ErrorCode.INTERNAL_ERROR)
            log.info("이슈 첨부를 올렸습니다. 이슈={} 첨부={} 크기={}", issueId, view.id, view.sizeBytes)
            return view
        } catch (exception: RuntimeException) {
            removeQuietly(files, key)
            throw exception
        }
    }

    fun attachments(user: LoginUser, issueId: Long): List<AttachmentView> {
        SecurityFrameworkUtils.requirePermission(user, Permissions.ISSUE_READ)
        val files = requireStorage()
        if (!issues.existsById(issueId)) throw BusinessException(ErrorCode.NOT_FOUND, "이슈를 찾을 수 없습니다.")
        return attachments.findByIssueIdOrderByCreatedAtAscIdAsc(issueId).map { toView(it, files) }
    }

    private fun toView(attachment: IssueAttachment, files: FileStorage?): AttachmentView {
        val link = files?.downloadLink(attachment.objectKey, attachment.fileName)
        return AttachmentView(
            id = attachment.requireId(),
            issueId = attachment.issueId,
            fileName = attachment.fileName,
            contentType = attachment.contentType,
            sizeBytes = attachment.sizeBytes,
            uploadedBy = attachment.uploadedBy,
            createdAt = SeoulTime.toOffset(attachment.createdAt),
            downloadUrl = link?.url?.toString(),
            downloadExpiresAt = link?.expiresAt?.atZone(clock.zone)?.toOffsetDateTime(),
        )
    }

    private fun requireStorage(): FileStorage =
        storage.ifAvailable ?: throw BusinessException(
            ErrorCode.SERVICE_NOT_CONFIGURED,
            "파일 저장소 설정이 없습니다. 환경변수 FILE_BUCKET 을 확인하세요.",
        )

    private fun removeQuietly(files: FileStorage, key: String) {
        try {
            files.delete(key)
        } catch (exception: RuntimeException) {
            log.error("첨부 정보 저장에 실패해 올린 파일을 지우려 했지만 지우지 못했습니다. 키={}", key, exception)
        }
    }

    companion object {
        private const val RESOURCE_TYPE = "issue_attachment"
        private const val MAX_PER_ISSUE = 20L
    }
}
