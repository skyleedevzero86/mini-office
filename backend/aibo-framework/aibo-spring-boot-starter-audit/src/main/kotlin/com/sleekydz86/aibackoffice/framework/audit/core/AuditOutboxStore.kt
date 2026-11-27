package com.sleekydz86.aibackoffice.framework.audit.core

import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

data class OutboxRow(val id: Long, val payload: String, val retryCount: Int)

open class AuditOutboxStore(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
) {

    open fun insert(payload: String) {
        jdbc.update("INSERT INTO audit_outbox (payload, status, retry_count, next_retry_at) VALUES (?, 'PENDING', 0, ?)",
            payload, Timestamp.valueOf(LocalDateTime.now()))
    }

    open fun claim(batchSize: Int, lease: Duration): List<OutboxRow> = transactions.execute {
        val now = LocalDateTime.now()
        val rows = jdbc.query(
            "SELECT id, payload, retry_count FROM audit_outbox WHERE status = 'PENDING' AND next_retry_at <= ? " +
                "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
            { rs, _ -> OutboxRow(rs.getLong("id"), rs.getString("payload"), rs.getInt("retry_count")) },
            Timestamp.valueOf(now), batchSize,
        )
        if (rows.isNotEmpty()) {
            val leaseUntil = Timestamp.valueOf(now.plus(lease))
            jdbc.batchUpdate("UPDATE audit_outbox SET next_retry_at = ? WHERE id = ?", rows.map { arrayOf<Any>(leaseUntil, it.id) })
        }
        rows
    } ?: emptyList()

    open fun markSent(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        jdbc.batchUpdate("UPDATE audit_outbox SET status = 'SENT' WHERE id = ?", ids.map { arrayOf<Any>(it) })
    }

    open fun markRetry(row: OutboxRow, nextRetryAt: LocalDateTime, giveUp: Boolean) {
        jdbc.update(
            "UPDATE audit_outbox SET retry_count = ?, next_retry_at = ?, status = ? WHERE id = ?",
            row.retryCount + 1, Timestamp.valueOf(nextRetryAt), if (giveUp) "FAILED" else "PENDING", row.id,
        )
    }
}
