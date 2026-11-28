package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
import com.sleekydz86.aibackoffice.mcp.domain.ToolPolicy

interface ToolPolicySource {
    fun all(): List<ToolPolicy>

    fun find(toolName: String): ToolPolicy? = all().firstOrNull { it.toolName == toolName }
}

data class ApprovalDraft(
    val toolName: String,
    val arguments: Map<String, Any?>,
    val reason: String?,
    val scopeChannel: String?,
    val conversationId: String?,
    val idempotencyKey: String?,
)

interface ApprovalGateway {
    fun request(user: LoginUser, draft: ApprovalDraft): ApprovalResponse
}

sealed interface LedgerEntry {
    data object Started : LedgerEntry

    data object Running : LedgerEntry

    data class Done(val resultJson: String) : LedgerEntry
}

interface ExecutionLedger {
    fun begin(approvalId: Long): LedgerEntry

    fun finish(approvalId: Long, resultJson: String)
}

data class CallMeta(
    val conversationId: String? = null,
    val idempotencyKey: String? = null,
)
