package com.sleekydz86.aibackoffice.common

object Permissions {
    const val CONTENT_READ = "content:read"
    const val CONTENT_WRITE = "content:write"
    const val SCHEDULE_READ = "schedule:read"
    const val SCHEDULE_DRAFT = "schedule:draft"
    const val SCHEDULE_PUBLISH = "schedule:publish"
    const val ISSUE_READ = "issue:read"
    const val ISSUE_WRITE = "issue:write"
    const val EXPOSURE_WRITE = "exposure:write"
    const val APPROVAL_DECIDE = "approval:decide"
    const val AUDIT_READ = "audit:read"
    const val POLICY_WRITE = "policy:write"

    const val APPROVAL_READ_SELF = "approval:read:self"

    val ALL = listOf(
        CONTENT_READ, CONTENT_WRITE, SCHEDULE_READ, SCHEDULE_DRAFT, SCHEDULE_PUBLISH,
        ISSUE_READ, ISSUE_WRITE, EXPOSURE_WRITE, APPROVAL_DECIDE, AUDIT_READ, POLICY_WRITE,
    )
}

enum class RiskLevel { READ, WRITE_LOW, WRITE_HIGH }

enum class ActorType { HUMAN, AGENT, SYSTEM }

object Headers {
    const val TRACE_ID = "X-Trace-Id"
    const val IDEMPOTENCY_KEY = "Idempotency-Key"
    const val APPROVAL_ID = "X-Approval-Id"
    const val ACTOR_TYPE = "X-Actor-Type"
    const val CONVERSATION_ID = "X-Conversation-Id"
    const val TOTAL_COUNT = "x-total-count"
}
