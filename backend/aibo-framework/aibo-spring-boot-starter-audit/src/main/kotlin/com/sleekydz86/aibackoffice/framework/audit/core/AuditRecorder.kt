package com.sleekydz86.aibackoffice.framework.audit.core

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditEvent
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

open class AuditRecorder(
    private val store: AuditOutboxStore,
    private val jsonMapper: JsonMapper,
    private val serviceName: String,
) {

    @Transactional
    open fun record(event: AuditEvent) {
        store.insert(jsonMapper.writeValueAsString(event))
    }

    @Transactional
    open fun record(
        user: LoginUser,
        action: String,
        result: AuditResult,
        resourceType: String? = null,
        resourceId: String? = null,
        beforeValue: Any? = null,
        afterValue: Any? = null,
        reason: String? = null,
        approvalId: Long? = null,
    ) {
        val actorType = when {
            user.service -> ActorType.SYSTEM
            else -> user.actorType
        }
        record(
            AuditEvent(
                traceId = TraceIds.current(),
                actorType = actorType,
                actorId = user.userId,
                onBehalfOf = user.onBehalfOf,
                service = serviceName,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                beforeValue = beforeValue,
                afterValue = afterValue,
                result = result,
                reason = reason,
                approvalId = approvalId,
            ),
        )
    }
}
