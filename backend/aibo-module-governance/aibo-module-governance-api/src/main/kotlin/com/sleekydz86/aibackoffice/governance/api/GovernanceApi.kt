package com.sleekydz86.aibackoffice.governance.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange

@HttpExchange("/api/governance")
interface GovernanceApi {

    @GetExchange("/approvals")
    fun approvals(
        @RequestParam(required = false) status: String?,
        @RequestParam mine: Boolean,
        @RequestParam("_start") start: Int,
        @RequestParam("_end") end: Int,
    ): ResponseEntity<List<ApprovalResponse>>

    @GetExchange("/approvals/{approvalId}")
    fun approval(@PathVariable approvalId: Long): ApprovalResponse

    @GetExchange("/audit-logs")
    fun auditLogs(
        @RequestParam(required = false) actorType: String?,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(required = false) resourceId: String?,
        @RequestParam(required = false) result: String?,
        @RequestParam(required = false) traceId: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam("_start") start: Int,
        @RequestParam("_end") end: Int,
    ): ResponseEntity<List<Map<String, Any?>>>

    @GetExchange("/automation-candidates")
    fun automationCandidates(
        @RequestParam days: Int,
        @RequestParam limit: Int,
    ): List<Map<String, Any?>>
}
