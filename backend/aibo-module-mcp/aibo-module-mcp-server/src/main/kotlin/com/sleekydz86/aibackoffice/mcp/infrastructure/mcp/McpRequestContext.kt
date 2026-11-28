package com.sleekydz86.aibackoffice.mcp.infrastructure.mcp

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import com.sleekydz86.aibackoffice.mcp.application.CallMeta
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.server.McpTransportContextExtractor
import jakarta.servlet.http.HttpServletRequest

object McpRequestContext {
    const val USER = "aibo.user"
    const val TRACE_ID = "aibo.traceId"
    const val CONVERSATION_ID = "aibo.conversationId"
    const val IDEMPOTENCY_KEY = "aibo.idempotencyKey"

    private const val MAX_HEADER = 64

    fun extractor(): McpTransportContextExtractor<HttpServletRequest> = McpTransportContextExtractor { request ->
        val values = buildMap<String, Any> {
            SecurityFrameworkUtils.loginUserOrNull()?.let { put(USER, it) }
            TraceIds.current()?.let { put(TRACE_ID, it) }
            header(request, Headers.CONVERSATION_ID)?.let { put(CONVERSATION_ID, it) }
            header(request, Headers.IDEMPOTENCY_KEY)?.let { put(IDEMPOTENCY_KEY, it) }
        }
        McpTransportContext.create(values)
    }

    fun user(context: McpTransportContext): LoginUser? = context.get(USER) as? LoginUser

    fun traceId(context: McpTransportContext): String? = context.get(TRACE_ID) as? String

    fun meta(context: McpTransportContext): CallMeta =
        CallMeta(context.get(CONVERSATION_ID) as? String, context.get(IDEMPOTENCY_KEY) as? String)

    private fun header(request: HttpServletRequest, name: String): String? =
        request.getHeader(name)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_HEADER }
}
