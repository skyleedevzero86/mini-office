package com.sleekydz86.aibackoffice.operation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "issue_attachments")
class IssueAttachment(
    @Column(name = "issue_id", nullable = false)
    var issueId: Long,
    @Column(name = "object_key", nullable = false, length = 300)
    var objectKey: String,
    @Column(name = "file_name", nullable = false, length = 200)
    var fileName: String,
    @Column(name = "content_type", nullable = false, length = 100)
    var contentType: String,
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,
    @Column(name = "uploaded_by", nullable = false, length = 100)
    var uploadedBy: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    fun requireId(): Long = id ?: throw IllegalStateException("저장되지 않은 첨부입니다.")
}
