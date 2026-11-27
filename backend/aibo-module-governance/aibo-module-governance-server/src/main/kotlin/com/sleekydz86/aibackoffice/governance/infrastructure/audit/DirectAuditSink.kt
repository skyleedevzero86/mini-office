package com.sleekydz86.aibackoffice.governance.infrastructure.audit

import com.sleekydz86.aibackoffice.framework.audit.core.AuditSink
import com.sleekydz86.aibackoffice.governance.application.AuditLogService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class DirectAuditSink(@Lazy private val auditLogs: AuditLogService) : AuditSink {

    override fun send(payloads: List<String>) {
        auditLogs.ingestPayloads(payloads)
    }
}
