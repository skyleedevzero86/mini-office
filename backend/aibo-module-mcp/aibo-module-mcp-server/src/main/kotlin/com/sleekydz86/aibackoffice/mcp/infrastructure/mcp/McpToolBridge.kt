package com.sleekydz86.aibackoffice.mcp.infrastructure.mcp

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import com.sleekydz86.aibackoffice.mcp.application.ToolInvocationService
import com.sleekydz86.aibackoffice.mcp.application.ToolResult
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolDefinition
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.spec.McpSchema
import tools.jackson.databind.json.JsonMapper

class McpToolBridge(
    private val invocations: ToolInvocationService,
    private val jsonMapper: JsonMapper,
) {

    fun call(context: McpTransportContext, request: McpSchema.CallToolRequest): McpSchema.CallToolResult {
        val user = McpRequestContext.user(context)
            ?: return toMcp(ToolResult.fail(ErrorCode.UNAUTHORIZED, "사용자 토큰이 필요합니다."))
        val result = TraceIds.runWith(McpRequestContext.traceId(context)) {
            invocations.call(user, request.name(), request.arguments().orEmpty(), McpRequestContext.meta(context))
        }
        return toMcp(result)
    }

    fun toMcp(result: ToolResult): McpSchema.CallToolResult {
        @Suppress("UNCHECKED_CAST")
        val structured = jsonMapper.convertValue(result, Map::class.java) as Map<String, Any?>
        return McpSchema.CallToolResult.builder()
            .addTextContent(jsonMapper.writeValueAsString(result))
            .structuredContent(structured)
            .isError(!result.ok && result.code != ErrorCode.PENDING_APPROVAL.name)
            .build()
    }

    companion object {
        fun toMcpTool(definition: ToolDefinition): McpSchema.Tool = McpSchema.Tool.builder()
            .name(definition.name)
            .title(definition.title)
            .description(definition.description)
            .inputSchema(definition.inputSchema)
            .build()
    }
}
