package com.sleekydz86.aibackoffice.common

import java.time.OffsetDateTime

data class AuditEvent(
    val traceId: String?,
    val actorType: ActorType,
    val actorId: String,
    val onBehalfOf: String? = null,
    val service: String,
    val action: String,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val beforeValue: Any? = null,
    val afterValue: Any? = null,
    val result: AuditResult,
    val reason: String? = null,
    val approvalId: Long? = null,
    val occurredAt: OffsetDateTime = OffsetDateTime.now(Zones.SEOUL),
)

enum class AuditResult { SUCCESS, DENIED, PENDING, FAILED }
