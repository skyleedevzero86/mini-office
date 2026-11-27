package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.file.config.AiboFileProperties
import com.sleekydz86.aibackoffice.framework.file.core.DownloadLink
import com.sleekydz86.aibackoffice.framework.file.core.FileRules
import com.sleekydz86.aibackoffice.framework.file.core.FileStorage
import com.sleekydz86.aibackoffice.framework.file.core.StoredFile
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.operation.domain.IssueAttachment
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueAttachmentRepository
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IssueRepository
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class AttachmentServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)

    private val issues = mock<IssueRepository>()
    private val attachments = mock<IssueAttachmentRepository>()
    private val files = mock<FileStorage>()
    private val audit = mock<AuditRecorder>()
    private val rules = FileRules(AiboFileProperties(maxSizeBytes = 1000))
    private val transactions = TransactionTemplate(
        mock<PlatformTransactionManager> { on { getTransaction(anyOrNull()) } doReturn SimpleTransactionStatus() },
    )

    private fun service(storage: FileStorage? = files): AttachmentService {
        val objects = mock<ObjectProvider<FileStorage>>()
        whenever(objects.ifAvailable).thenReturn(storage)
        return AttachmentService(issues, attachments, objects, rules, audit, transactions, clock)
    }

    private val user = LoginUser(
        userId = "7", name = "운영 담당", team = null, roles = emptySet(),
        perms = setOf(Permissions.ISSUE_READ, Permissions.ISSUE_WRITE), scopes = emptySet(),
        permissionVersion = 0, tokenId = null, tokenType = TokenType.USER, onBehalfOf = null,
        actorType = ActorType.HUMAN, rawToken = "token",
    )

    private fun upload(size: Long = 3, type: String = "image/png") =
        AttachmentUpload("캡처.png", type, size) { ByteArrayInputStream(ByteArray(size.toInt())) }

    @Test
    @DisplayName("파일을 저장소에 올린 뒤 첨부 정보를 저장하고 감사 기록을 남긴다")
    fun uploadsAndSaves() {
        // given
        whenever(issues.existsById(3)).thenReturn(true)
        whenever(files.put(any(), any(), any(), any())).thenAnswer { StoredFile(it.getArgument(0), "image/png", 3) }
        whenever(attachments.save(any<IssueAttachment>())).thenAnswer { (it.arguments[0] as IssueAttachment).apply { id = 31 } }

        // when
        val view = service().upload(user, 3, upload())

        // then
        assertEquals(31L, view.id)
        assertEquals("캡처.png", view.fileName)
        assertEquals("7", view.uploadedBy)
        verify(audit).record(any(), eq("issue.attachment.upload"), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    @DisplayName("첨부 정보 저장이 실패하면 올린 파일을 지운다")
    fun removesFileWhenSaveFails() {
        // given
        whenever(issues.existsById(3)).thenReturn(true)
        whenever(attachments.save(any<IssueAttachment>())).thenThrow(IllegalStateException("저장 실패"))

        // when
        assertThrows<IllegalStateException> { service().upload(user, 3, upload()) }

        // then
        verify(files).put(any(), any(), any(), any())
        verify(files).delete(any())
    }

    @Test
    @DisplayName("허용하지 않은 파일은 저장소에 올리지 않는다")
    fun rejectsBeforeUpload() {
        // given
        whenever(issues.existsById(3)).thenReturn(true)

        // when
        val error = assertThrows<BusinessException> { service().upload(user, 3, upload(type = "text/html")) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
        verify(files, never()).put(any(), any(), any(), any())
    }

    @Test
    @DisplayName("파일 저장소 설정이 없으면 503으로 알려 준다")
    fun storageNotConfigured() {
        // when
        val error = assertThrows<BusinessException> { service(storage = null).upload(user, 3, upload()) }

        // then
        assertEquals(ErrorCode.SERVICE_NOT_CONFIGURED, error.code)
    }

    @Test
    @DisplayName("첨부 목록에는 서명된 내려받기 주소와 만료 시각을 붙인다")
    fun listsWithDownloadLinks() {
        // given
        whenever(issues.existsById(3)).thenReturn(true)
        val saved = IssueAttachment(3, "issues/3/k-a.png", "a.png", "image/png", 3, "7", now).apply { id = 31 }
        whenever(attachments.findByIssueIdOrderByCreatedAtAscIdAsc(3)).thenReturn(listOf(saved))
        whenever(files.downloadLink("issues/3/k-a.png", "a.png", null))
            .thenReturn(DownloadLink(URI.create("http://localhost:9000/f?sig=1").toURL(), Instant.parse("2026-12-04T01:10:00Z")))

        // when
        val list = service().attachments(user, 3)

        // then
        assertEquals("http://localhost:9000/f?sig=1", list.single().downloadUrl)
        assertEquals(LocalDateTime.of(2026, 12, 4, 10, 10), list.single().downloadExpiresAt?.toLocalDateTime())
    }
}
