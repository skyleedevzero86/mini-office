package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.file.core.FileRules
import com.sleekydz86.aibackoffice.framework.file.core.FileStorage
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.governance.domain.AuditLog
import com.sleekydz86.aibackoffice.governance.domain.AuditLogFilter
import com.sleekydz86.aibackoffice.governance.infrastructure.persistence.AuditLogStore
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

data class AuditLogView(
    val id: Long,
    val traceId: String?,
    val actorType: ActorType,
    val actorId: String,
    val onBehalfOf: String?,
    val service: String,
    val action: String,
    val resourceType: String?,
    val resourceId: String?,
    val beforeValue: Any?,
    val afterValue: Any?,
    val result: AuditResult,
    val reason: String?,
    val approvalId: Long?,
    val createdAt: OffsetDateTime,
)

data class AuditLogPage(val items: List<AuditLogView>, val total: Long)

data class AutomationCandidateView(
    val action: String,
    val count: Long,
    val actorCount: Long,
    val lastAt: OffsetDateTime,
    val suggestion: String,
)

data class AuditExportView(
    val fileName: String,
    val rows: Int,
    val truncated: Boolean,
    val downloadUrl: String,
    val expiresAt: OffsetDateTime,
)

@Service
class AuditLogService(
    private val logs: AuditLogStore,
    private val jsonMapper: JsonMapper,
    private val storage: ObjectProvider<FileStorage>,
    private val rules: FileRules,
    private val audit: AuditRecorder,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun ingestPayloads(payloads: List<String>): Int =
        ingest(payloads.mapNotNull { payload ->
            runCatching { jsonMapper.readValue(payload, MAP_TYPE) }
                .onFailure { log.error("읽을 수 없는 감사 기록을 건너뜁니다. 이유={}", it.message) }
                .getOrNull()
        })

    @Transactional
    fun ingest(events: List<Map<String, Any?>>): Int {
        if (events.size > MAX_BATCH) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "감사 기록은 한 번에 ${MAX_BATCH}건까지 받을 수 있습니다.")
        }
        val rows = events.mapNotNull { event ->
            runCatching { toLog(event) }
                .onFailure { log.error("형식이 맞지 않는 감사 기록을 건너뜁니다. 이유={}", it.message) }
                .getOrNull()
        }
        logs.insertAll(rows)
        return rows.size
    }

    @Transactional(readOnly = true)
    fun search(user: LoginUser, filter: AuditLogFilter, query: ListQuery): AuditLogPage {
        SecurityFrameworkUtils.requirePermission(user, Permissions.AUDIT_READ)
        val items = logs.search(filter, query.offset, query.limit, query.ascending).map(::view)
        return AuditLogPage(items, logs.count(filter))
    }

    fun export(user: LoginUser, filter: AuditLogFilter): AuditExportView {
        SecurityFrameworkUtils.requirePermission(user, Permissions.AUDIT_READ)
        val files = storage.ifAvailable ?: throw BusinessException(
            ErrorCode.SERVICE_NOT_CONFIGURED,
            "파일 저장소 설정이 없습니다. 환경변수 FILE_BUCKET 을 확인하세요.",
        )
        val total = logs.count(filter)
        val rows = logs.search(filter, 0, MAX_EXPORT_ROWS, ascending = false)
        val bytes = AuditCsv.write(rows)
        val now = LocalDateTime.now(clock)
        val fileName = "audit-logs-${now.format(FILE_TIME)}.csv"
        val key = rules.key("exports/audit", fileName)
        files.put(key, "text/csv", bytes.size.toLong(), ByteArrayInputStream(bytes))
        val link = files.downloadLink(key, fileName)
        audit.record(
            user,
            action = "audit.export",
            result = AuditResult.SUCCESS,
            resourceType = "audit_export",
            resourceId = key,
            afterValue = mapOf("rows" to rows.size, "filter" to filter.toString()),
        )
        log.info("감사 기록을 내보냈습니다. 건수={} 전체={} 요청자={}", rows.size, total, user.effectiveUserId)
        return AuditExportView(fileName, rows.size, total > rows.size, link.url.toString(), link.expiresAt.atZone(Zones.SEOUL).toOffsetDateTime())
    }

    @Transactional(readOnly = true)
    fun automationCandidates(user: LoginUser, days: Int, limit: Int): List<AutomationCandidateView> {
        SecurityFrameworkUtils.requirePermission(user, Permissions.AUDIT_READ)
        if (days !in 1..MAX_DAYS) throw BusinessException(ErrorCode.VALIDATION_FAILED, "기간은 1일부터 ${MAX_DAYS}일까지 고를 수 있습니다.")
        if (limit !in 1..MAX_CANDIDATES) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "개수는 1부터 ${MAX_CANDIDATES}까지 고를 수 있습니다.")
        }
        val since = LocalDateTime.now(clock).minusDays(days.toLong())
        return logs.humanActions(since, limit).map {
            AutomationCandidateView(
                action = it.action,
                count = it.count,
                actorCount = it.actorCount,
                lastAt = SeoulTime.toOffset(it.lastAt),
                suggestion = "최근 ${days}일 동안 ${it.actorCount}명이 ${it.count}번 직접 한 작업입니다. 에이전트 툴이나 자동 처리로 바꿀 수 있는지 검토해 보세요.",
            )
        }
    }

    private fun toLog(event: Map<String, Any?>): AuditLog {
        fun text(name: String, max: Int): String? = event[name]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(max)
        fun required(name: String, max: Int): String = text(name, max) ?: throw IllegalArgumentException("$name 값이 없습니다.")
        val occurredAt = text("occurredAt", 64)?.let { OffsetDateTime.parse(it).atZoneSameInstant(Zones.SEOUL).toLocalDateTime() }
            ?: LocalDateTime.now(clock)
        return AuditLog(
            id = null,
            traceId = text("traceId", 64),
            actorType = ActorType.valueOf(required("actorType", 10)),
            actorId = required("actorId", 100),
            onBehalfOf = text("onBehalfOf", 100),
            service = required("service", 30),
            action = required("action", 100),
            resourceType = text("resourceType", 50),
            resourceId = text("resourceId", 100),
            beforeJson = event["beforeValue"]?.let(jsonMapper::writeValueAsString),
            afterJson = event["afterValue"]?.let(jsonMapper::writeValueAsString),
            result = AuditResult.valueOf(required("result", 10)),
            reason = text("reason", 500),
            approvalId = (event["approvalId"] as? Number)?.toLong(),
            createdAt = occurredAt,
        )
    }

    private fun view(log: AuditLog): AuditLogView = AuditLogView(
        id = log.id ?: 0,
        traceId = log.traceId,
        actorType = log.actorType,
        actorId = log.actorId,
        onBehalfOf = log.onBehalfOf,
        service = log.service,
        action = log.action,
        resourceType = log.resourceType,
        resourceId = log.resourceId,
        beforeValue = log.beforeJson?.let { jsonMapper.readValue(it, Any::class.java) },
        afterValue = log.afterJson?.let { jsonMapper.readValue(it, Any::class.java) },
        result = log.result,
        reason = log.reason,
        approvalId = log.approvalId,
        createdAt = SeoulTime.toOffset(log.createdAt),
    )

    companion object {
        private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
        private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private const val MAX_BATCH = 500
        private const val MAX_EXPORT_ROWS = 10_000
        private const val MAX_DAYS = 30
        private const val MAX_CANDIDATES = 50
    }
}

object AuditCsv {

    private val HEADER = listOf(
        "id", "created_at", "trace_id", "actor_type", "actor_id", "on_behalf_of", "service", "action",
        "resource_type", "resource_id", "result", "reason", "approval_id", "before_value", "after_value",
    )
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    fun write(rows: List<AuditLog>): ByteArray {
        val text = buildString {
            appendLine(HEADER.joinToString(","))
            rows.forEach { row ->
                val cells = listOf(
                    row.id?.toString(), SeoulTime.toOffset(row.createdAt).toString(), row.traceId, row.actorType.name, row.actorId,
                    row.onBehalfOf, row.service, row.action, row.resourceType, row.resourceId, row.result.name, row.reason,
                    row.approvalId?.toString(), row.beforeJson, row.afterJson,
                )
                appendLine(cells.joinToString(",") { cell(it) })
            }
        }
        return BOM + text.toByteArray(Charsets.UTF_8)
    }

    fun cell(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val guarded = if (value.first() in FORMULA_START) "'$value" else value
        val needsQuote = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
    }
}
