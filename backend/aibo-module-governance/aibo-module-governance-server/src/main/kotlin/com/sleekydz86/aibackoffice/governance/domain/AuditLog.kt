package com.sleekydz86.aibackoffice.governance.domain

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditResult
import java.time.LocalDateTime

data class AuditLog(
    val id: Long?,
    val traceId: String?,
    val actorType: ActorType,
    val actorId: String,
    val onBehalfOf: String?,
    val service: String,
    val action: String,
    val resourceType: String?,
    val resourceId: String?,
    val beforeJson: String?,
    val afterJson: String?,
    val result: AuditResult,
    val reason: String?,
    val approvalId: Long?,
    val createdAt: LocalDateTime,
)

data class AuditLogFilter(
    val actorType: ActorType? = null,
    val actorId: String? = null,
    val action: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val result: AuditResult? = null,
    val traceId: String? = null,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
)

data class ActionCount(val action: String, val count: Long, val actorCount: Long, val lastAt: LocalDateTime)
