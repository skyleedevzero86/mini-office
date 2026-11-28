package com.sleekydz86.aibackoffice.mcp.infrastructure.mcp

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.mcp.application.ToolVisibilityService
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.server.McpStatelessServerHandler
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpStatelessServerTransport
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class VisibleToolsHandler(
    private val delegate: McpStatelessServerHandler,
    private val visibility: ToolVisibilityService,
) : McpStatelessServerHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleRequest(context: McpTransportContext, request: McpSchema.JSONRPCRequest): Mono<McpSchema.JSONRPCResponse> {
        if (request.method() != McpSchema.METHOD_TOOLS_LIST) return delegate.handleRequest(context, request)
        return Mono.fromCallable { listTools(context, request) }
    }

    override fun handleNotification(context: McpTransportContext, notification: McpSchema.JSONRPCNotification): Mono<Void> =
        delegate.handleNotification(context, notification)

    private fun listTools(context: McpTransportContext, request: McpSchema.JSONRPCRequest): McpSchema.JSONRPCResponse {
        val user = McpRequestContext.user(context)
            ?: return McpSchema.JSONRPCResponse.error(request.id(), McpSchema.JSONRPCResponse.JSONRPCError(UNAUTHORIZED, "사용자 토큰이 필요합니다."))
        return try {
            val tools = visibility.visible(user).map { McpToolBridge.toMcpTool(it.definition()) }
            McpSchema.JSONRPCResponse.result(request.id(), McpSchema.ListToolsResult(tools, null))
        } catch (exception: BusinessException) {
            log.warn("툴 목록을 만들지 못했습니다. 사용자={} 이유={}", user.effectiveUserId, exception.message)
            McpSchema.JSONRPCResponse.error(request.id(), McpSchema.JSONRPCResponse.JSONRPCError(INTERNAL, exception.message ?: "툴 목록을 만들지 못했습니다."))
        }
    }

    companion object {
        private const val UNAUTHORIZED = -32001
        private const val INTERNAL = -32603
    }
}

class VisibleToolsTransport(
    private val delegate: McpStatelessServerTransport,
    private val wrap: (McpStatelessServerHandler) -> McpStatelessServerHandler,
) : McpStatelessServerTransport {

    override fun setMcpHandler(mcpHandler: McpStatelessServerHandler) {
        delegate.setMcpHandler(wrap(mcpHandler))
    }

    override fun close() = delegate.close()

    override fun closeGracefully(): Mono<Void> = delegate.closeGracefully()

    override fun protocolVersions(): List<String> = delegate.protocolVersions()
}
