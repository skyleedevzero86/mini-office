package com.sleekydz86.aibackoffice.operation.infrastructure.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

data class StoredResponse(val requestHash: String, val status: Int, val body: String?)

@Component
class IdempotencyStore(private val jdbc: JdbcTemplate) {

    fun find(key: String): StoredResponse? =
        jdbc.query(
            "SELECT request_hash, response_status, response_body FROM idempotency_records WHERE idem_key = ?",
            { rs, _ -> StoredResponse(rs.getString("request_hash"), rs.getInt("response_status"), rs.getString("response_body")) },
            key,
        ).firstOrNull()

    fun insert(key: String, requestHash: String, status: Int, body: String) {
        jdbc.update(
            "INSERT INTO idempotency_records (idem_key, request_hash, response_status, response_body) VALUES (?, ?, ?, ?)",
            key,
            requestHash,
            status,
            body,
        )
    }
}
