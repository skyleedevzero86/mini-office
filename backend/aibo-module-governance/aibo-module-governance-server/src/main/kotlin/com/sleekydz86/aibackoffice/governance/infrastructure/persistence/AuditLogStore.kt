package com.sleekydz86.aibackoffice.governance.infrastructure.persistence

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.governance.domain.ActionCount
import com.sleekydz86.aibackoffice.governance.domain.AuditLog
import com.sleekydz86.aibackoffice.governance.domain.AuditLogFilter
import java.sql.ResultSet
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AuditLogStore(private val jdbc: JdbcTemplate) {

    fun insertAll(logs: List<AuditLog>) {
        if (logs.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO audit_logs (trace_id, actor_type, actor_id, on_behalf_of, service, action, resource_type, resource_id, " +
                "before_value, after_value, result, reason, approval_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            logs.map {
                arrayOf<Any?>(
                    it.traceId, it.actorType.name, it.actorId, it.onBehalfOf, it.service, it.action, it.resourceType, it.resourceId,
                    it.beforeJson, it.afterJson, it.result.name, it.reason, it.approvalId, it.createdAt,
                )
            },
        )
    }

    fun search(filter: AuditLogFilter, offset: Int, limit: Int, ascending: Boolean): List<AuditLog> {
        val (where, args) = where(filter)
        val direction = if (ascending) "ASC" else "DESC"
        return jdbc.query("$SELECT$where ORDER BY created_at $direction, id $direction LIMIT ? OFFSET ?", ::map, *args, limit, offset)
    }

    fun count(filter: AuditLogFilter): Long {
        val (where, args) = where(filter)
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs$where", Long::class.java, *args) ?: 0
    }

    fun humanActions(since: LocalDateTime, limit: Int): List<ActionCount> =
        jdbc.query(
            "SELECT action, COUNT(*) AS total, COUNT(DISTINCT actor_id) AS actors, MAX(created_at) AS last_at FROM audit_logs " +
                "WHERE actor_type = ? AND result = ? AND created_at >= ? GROUP BY action ORDER BY total DESC, action LIMIT ?",
            { rs, _ -> ActionCount(rs.getString("action"), rs.getLong("total"), rs.getLong("actors"), rs.getObject("last_at", LocalDateTime::class.java)) },
            ActorType.HUMAN.name,
            AuditResult.SUCCESS.name,
            since,
            limit,
        )

    private fun where(filter: AuditLogFilter): Pair<String, Array<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        fun add(clause: String, value: Any?) {
            if (value == null) return
            clauses += clause
            args += value
        }
        add("actor_type = ?", filter.actorType?.name)
        add("actor_id = ?", filter.actorId)
        add("action = ?", filter.action)
        add("resource_type = ?", filter.resourceType)
        add("resource_id = ?", filter.resourceId)
        add("result = ?", filter.result?.name)
        add("trace_id = ?", filter.traceId)
        add("created_at >= ?", filter.from)
        add("created_at < ?", filter.to)
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        return where to args.toTypedArray()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): AuditLog = AuditLog(
        id = rs.getLong("id"),
        traceId = rs.getString("trace_id"),
        actorType = ActorType.valueOf(rs.getString("actor_type")),
        actorId = rs.getString("actor_id"),
        onBehalfOf = rs.getString("on_behalf_of"),
        service = rs.getString("service"),
        action = rs.getString("action"),
        resourceType = rs.getString("resource_type"),
        resourceId = rs.getString("resource_id"),
        beforeJson = rs.getString("before_value"),
        afterJson = rs.getString("after_value"),
        result = AuditResult.valueOf(rs.getString("result")),
        reason = rs.getString("reason"),
        approvalId = rs.getObject("approval_id", java.lang.Long::class.java)?.toLong(),
        createdAt = rs.getObject("created_at", LocalDateTime::class.java),
    )

    companion object {
        private const val SELECT =
            "SELECT id, trace_id, actor_type, actor_id, on_behalf_of, service, action, resource_type, resource_id, before_value, " +
                "after_value, result, reason, approval_id, created_at FROM audit_logs"
    }
}
