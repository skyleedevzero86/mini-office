package com.sleekydz86.aibackoffice.operation.infrastructure.persistence

import com.sleekydz86.aibackoffice.operation.domain.ContentExposure
import java.sql.ResultSet
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class ContentExposureStore(private val jdbc: JdbcTemplate) {

    fun isHidden(contentId: Long): Boolean = find(contentId)?.exposed == false

    fun find(contentId: Long): ContentExposure? =
        jdbc.query("$SELECT WHERE content_id = ?", ::map, contentId).firstOrNull()

    fun findForUpdate(contentId: Long): ContentExposure? =
        jdbc.query("$SELECT WHERE content_id = ? FOR UPDATE", ::map, contentId).firstOrNull()

    fun list(exposed: Boolean?, offset: Int, limit: Int, sortColumn: String, ascending: Boolean): List<ContentExposure> {
        val direction = if (ascending) "ASC" else "DESC"
        val column = SORT_COLUMNS[sortColumn] ?: SORT_COLUMNS.getValue("updatedAt")
        val order = " ORDER BY $column $direction, content_id $direction LIMIT ? OFFSET ?"
        return if (exposed == null) {
            jdbc.query(SELECT + order, ::map, limit, offset)
        } else {
            jdbc.query("$SELECT WHERE exposed = ?$order", ::map, exposed, limit, offset)
        }
    }

    fun count(exposed: Boolean?): Long =
        if (exposed == null) {
            jdbc.queryForObject("SELECT COUNT(*) FROM content_exposure", Long::class.java) ?: 0
        } else {
            jdbc.queryForObject("SELECT COUNT(*) FROM content_exposure WHERE exposed = ?", Long::class.java, exposed) ?: 0
        }

    fun insert(exposure: ContentExposure) {
        jdbc.update(
            "INSERT INTO content_exposure (content_id, exposed, reason, issue_id, approval_id, updated_by, updated_at, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
            exposure.contentId,
            exposure.exposed,
            exposure.reason,
            exposure.issueId,
            exposure.approvalId,
            exposure.updatedBy,
            exposure.updatedAt,
        )
    }

    fun update(exposure: ContentExposure, expectedVersion: Int): Boolean =
        jdbc.update(
            "UPDATE content_exposure SET exposed = ?, reason = ?, issue_id = ?, approval_id = ?, updated_by = ?, updated_at = ?, " +
                "version = version + 1 WHERE content_id = ? AND version = ?",
            exposure.exposed,
            exposure.reason,
            exposure.issueId,
            exposure.approvalId,
            exposure.updatedBy,
            exposure.updatedAt,
            exposure.contentId,
            expectedVersion,
        ) == 1

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): ContentExposure = ContentExposure(
        contentId = rs.getLong("content_id"),
        exposed = rs.getBoolean("exposed"),
        reason = rs.getString("reason"),
        issueId = rs.getObject("issue_id", java.lang.Long::class.java)?.toLong(),
        approvalId = rs.getObject("approval_id", java.lang.Long::class.java)?.toLong(),
        updatedBy = rs.getString("updated_by"),
        updatedAt = rs.getObject("updated_at", LocalDateTime::class.java),
        version = rs.getInt("version"),
    )

    companion object {
        private const val SELECT =
            "SELECT content_id, exposed, reason, issue_id, approval_id, updated_by, updated_at, version FROM content_exposure "
        private val SORT_COLUMNS = mapOf("updatedAt" to "updated_at", "contentId" to "content_id")
    }
}
