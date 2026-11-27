package com.sleekydz86.aibackoffice.governance.presentation

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.framework.web.core.list.ListResponses
import com.sleekydz86.aibackoffice.governance.application.AuditExportView
import com.sleekydz86.aibackoffice.governance.application.AuditLogService
import com.sleekydz86.aibackoffice.governance.application.AuditLogView
import com.sleekydz86.aibackoffice.governance.application.AutomationCandidateView
import com.sleekydz86.aibackoffice.governance.domain.AuditLogFilter
import com.sleekydz86.aibackoffice.common.Zones
import java.time.OffsetDateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/governance")
class AuditController(private val auditLogService: AuditLogService) {

    @GetMapping("/audit-logs")
    @PreAuthorize("@ss.hasPermission('audit:read')")
    fun auditLogs(
        @RequestParam(required = false) actorType: ActorType?,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(required = false) resourceId: String?,
        @RequestParam(required = false) result: AuditResult?,
        @RequestParam(required = false) traceId: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime?,
        query: ListQuery,
    ): ResponseEntity<List<AuditLogView>> {
        val filter = filter(actorType, actorId, action, resourceType, resourceId, result, traceId, from, to)
        val page = auditLogService.search(SecurityFrameworkUtils.loginUser(), filter, query)
        return ListResponses.of(page.items, page.total)
    }

    @PostMapping("/audit-logs/export")
    @PreAuthorize("@ss.hasPermission('audit:read')")
    fun export(
        @RequestParam(required = false) actorType: ActorType?,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(required = false) resourceId: String?,
        @RequestParam(required = false) result: AuditResult?,
        @RequestParam(required = false) traceId: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime?,
    ): AuditExportView = auditLogService.export(
        SecurityFrameworkUtils.loginUser(),
        filter(actorType, actorId, action, resourceType, resourceId, result, traceId, from, to),
    )

    @GetMapping("/automation-candidates")
    @PreAuthorize("@ss.hasPermission('audit:read')")
    fun automationCandidates(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<AutomationCandidateView> = auditLogService.automationCandidates(SecurityFrameworkUtils.loginUser(), days, limit)

    private fun filter(
        actorType: ActorType?,
        actorId: String?,
        action: String?,
        resourceType: String?,
        resourceId: String?,
        result: AuditResult?,
        traceId: String?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ) = AuditLogFilter(
        actorType = actorType,
        actorId = actorId?.trim()?.takeIf { it.isNotEmpty() },
        action = action?.trim()?.takeIf { it.isNotEmpty() },
        resourceType = resourceType?.trim()?.takeIf { it.isNotEmpty() },
        resourceId = resourceId?.trim()?.takeIf { it.isNotEmpty() },
        result = result,
        traceId = traceId?.trim()?.takeIf { it.isNotEmpty() },
        from = from?.atZoneSameInstant(Zones.SEOUL)?.toLocalDateTime(),
        to = to?.atZoneSameInstant(Zones.SEOUL)?.toLocalDateTime(),
    )
}
