package com.sleekydz86.aibackoffice.governance.infrastructure.persistence

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.governance.domain.ApprovalRequest
import com.sleekydz86.aibackoffice.governance.domain.ApprovalStatus
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component

@Component
class ApprovalStore(private val jdbc: JdbcTemplate) {

    fun insert(request: ApprovalRequest): Long {
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                "INSERT INTO approval_requests (tool_name, arguments, risk_level, requester_id, requester_name, requester_type, " +
                    "conversation_id, scope_channel, reason, status, expires_at, idempotency_key, trace_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            ).apply {
                setString(1, request.toolName)
                setString(2, request.argumentsJson)
                setString(3, request.riskLevel.name)
                setString(4, request.requesterId)
                setString(5, request.requesterName)
                setString(6, request.requesterType.name)
                setString(7, request.conversationId)
                setString(8, request.scopeChannel)
                setString(9, request.reason)
                setString(10, request.status.name)
                setObject(11, request.expiresAt)
                setString(12, request.idempotencyKey)
                setString(13, request.traceId)
                setObject(14, request.createdAt)
            }
        }, keys)
        return keys.key?.toLong() ?: throw IllegalStateException("승인 요청 번호를 받지 못했습니다.")
    }

    fun find(id: Long): ApprovalRequest? = jdbc.query("$SELECT WHERE id = ?", ::map, id).firstOrNull()

    fun findByIdempotencyKey(key: String): ApprovalRequest? =
        jdbc.query("$SELECT WHERE idempotency_key = ?", ::map, key).firstOrNull()

    fun list(status: ApprovalStatus?, requesterId: String?, offset: Int, limit: Int, ascending: Boolean): List<ApprovalRequest> {
        val (where, args) = filter(status, requesterId)
        val direction = if (ascending) "ASC" else "DESC"
        return jdbc.query("$SELECT$where ORDER BY created_at $direction, id $direction LIMIT ? OFFSET ?", ::map, *args, limit, offset)
    }

    fun count(status: ApprovalStatus?, requesterId: String?): Long {
        val (where, args) = filter(status, requesterId)
        return jdbc.queryForObject("SELECT COUNT(*) FROM approval_requests$where", Long::class.java, *args) ?: 0
    }

    fun decide(id: Long, from: ApprovalStatus, to: ApprovalStatus, approverId: String?, comment: String?, at: LocalDateTime): Boolean =
        jdbc.update(
            "UPDATE approval_requests SET status = ?, approver_id = ?, decision_comment = ?, decided_at = ? WHERE id = ? AND status = ?",
            to.name,
            approverId,
            comment,
            at,
            id,
            from.name,
        ) == 1

    fun finish(id: Long, to: ApprovalStatus, resultJson: String): Boolean =
        jdbc.update(
            "UPDATE approval_requests SET status = ?, result = ? WHERE id = ? AND status = ?",
            to.name,
            resultJson,
            id,
            ApprovalStatus.APPROVED.name,
        ) == 1

    fun findExpired(now: LocalDateTime, limit: Int): List<Long> =
        jdbc.queryForList(
            "SELECT id FROM approval_requests WHERE status = ? AND expires_at <= ? ORDER BY expires_at LIMIT ?",
            Long::class.java,
            ApprovalStatus.PENDING.name,
            now,
            limit,
        ).filterNotNull()

    private fun filter(status: ApprovalStatus?, requesterId: String?): Pair<String, Array<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        status?.let {
            clauses += "status = ?"
            args += it.name
        }
        requesterId?.let {
            clauses += "requester_id = ?"
            args += it
        }
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        return where to args.toTypedArray()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): ApprovalRequest = ApprovalRequest(
        id = rs.getLong("id"),
        toolName = rs.getString("tool_name"),
        argumentsJson = rs.getString("arguments"),
        riskLevel = RiskLevel.valueOf(rs.getString("risk_level")),
        requesterId = rs.getString("requester_id"),
        requesterName = rs.getString("requester_name"),
        requesterType = ActorType.valueOf(rs.getString("requester_type")),
        conversationId = rs.getString("conversation_id"),
        scopeChannel = rs.getString("scope_channel"),
        reason = rs.getString("reason"),
        status = ApprovalStatus.valueOf(rs.getString("status")),
        approverId = rs.getString("approver_id"),
        decisionComment = rs.getString("decision_comment"),
        decidedAt = rs.getObject("decided_at", LocalDateTime::class.java),
        expiresAt = rs.getObject("expires_at", LocalDateTime::class.java),
        idempotencyKey = rs.getString("idempotency_key"),
        resultJson = rs.getString("result"),
        traceId = rs.getString("trace_id"),
        createdAt = rs.getObject("created_at", LocalDateTime::class.java),
    )

    companion object {
        private const val SELECT =
            "SELECT id, tool_name, arguments, risk_level, requester_id, requester_name, requester_type, conversation_id, scope_channel, " +
                "reason, status, approver_id, decision_comment, decided_at, expires_at, idempotency_key, result, trace_id, created_at " +
                "FROM approval_requests "
    }
}
