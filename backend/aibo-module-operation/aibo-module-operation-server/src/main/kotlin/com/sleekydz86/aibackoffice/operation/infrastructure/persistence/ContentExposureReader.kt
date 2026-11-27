package com.sleekydz86.aibackoffice.operation.infrastructure.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class ContentExposureReader(private val jdbc: JdbcTemplate) {

    fun isHidden(contentId: Long): Boolean =
        jdbc.query(
            "SELECT exposed FROM content_exposure WHERE content_id = ?",
            { rs, _ -> rs.getBoolean("exposed") },
            contentId,
        ).firstOrNull() == false
}
