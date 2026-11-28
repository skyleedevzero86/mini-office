package com.sleekydz86.aibackoffice.agent.infrastructure.mcp

import com.sleekydz86.aibackoffice.agent.application.AgentToolSpec
import com.sleekydz86.aibackoffice.agent.application.ToolCallOutcome
import com.sleekydz86.aibackoffice.agent.application.ToolGateway
import com.sleekydz86.aibackoffice.agent.application.ToolSession
import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.spec.McpSchema
import java.time.Duration
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

class McpToolGateway(
    private val baseUrl: String,
    private val endpoint: String,
    private val timeout: Duration,
    private val jsonMapper: JsonMapper,
) : ToolGateway {

    override fun open(user: LoginUser, conversationId: String?, traceId: String?): ToolSession {
        val transport = HttpClientStreamableHttpTransport.builder(baseUrl.trimEnd('/'))
            .endpoint(endpoint)
            .jsonMapper(McpJsonDefaults.getMapper())
            .connectTimeout(timeout)
            .httpRequestCustomizer { builder, _, _, _, _ ->
                builder.header("Authorization", "Bearer ${user.rawToken}")
                builder.header(Headers.ACTOR_TYPE, ActorType.AGENT.name)
                conversationId?.let { builder.header(Headers.CONVERSATION_ID, it) }
                traceId?.let { builder.header(Headers.TRACE_ID, it) }
            }
            .build()
        val client = McpClient.sync(transport)
            .clientInfo(McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
            .requestTimeout(timeout)
            .initializationTimeout(timeout)
            .build()
        return McpToolSession(client, jsonMapper)
    }

    companion object {
        const val CLIENT_NAME = "aibo-agent"
        const val CLIENT_VERSION = "0.1.0"
    }
}

class McpToolSession(
    private val client: McpSyncClient,
    private val jsonMapper: JsonMapper,
) : ToolSession {
    private val log = LoggerFactory.getLogger(javaClass)
    private var initialized = false

    override fun tools(): List<AgentToolSpec> {
        ensureInitialized()
        val result = mutableListOf<AgentToolSpec>()
        var cursor: String? = null
        do {
            val page = client.listTools(cursor)
            page.tools().forEach { tool ->
                result += AgentToolSpec(
                    name = tool.name(),
                    title = tool.title(),
                    description = tool.description().orEmpty(),
                    inputSchemaJson = jsonMapper.writeValueAsString(tool.inputSchema() ?: mapOf("type" to "object")),
                )
            }
            cursor = page.nextCursor()?.takeIf { it.isNotBlank() }
        } while (cursor != null && result.size < MAX_TOOLS)
        return result
    }

    override fun call(name: String, argumentsJson: String): ToolCallOutcome {
        ensureInitialized()
        val arguments = parseArguments(argumentsJson)
            ?: return invalidArguments()
        val result = client.callTool(McpSchema.CallToolRequest(name, arguments))
        return toOutcome(result)
    }

    override fun close() {
        try {
            client.closeGracefully()
        } catch (e: RuntimeException) {
            log.debug("툴 서버 연결을 닫다가 오류가 났습니다.", e)
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            client.initialize()
            initialized = true
        }
    }

    private fun parseArguments(json: String): Map<String, Any?>? = try {
        @Suppress("UNCHECKED_CAST")
        jsonMapper.readValue(json.ifBlank { "{}" }, Map::class.java) as Map<String, Any?>
    } catch (e: RuntimeException) {
        null
    }

    private fun invalidArguments(): ToolCallOutcome {
        val message = "툴에 넘긴 값이 JSON 객체가 아닙니다."
        val content = jsonMapper.writeValueAsString(mapOf("ok" to false, "code" to ErrorCode.VALIDATION_FAILED.name, "message" to message))
        return ToolCallOutcome(false, ErrorCode.VALIDATION_FAILED.name, message, null, content)
    }

    private fun toOutcome(result: McpSchema.CallToolResult): ToolCallOutcome {
        val text = result.content().filterIsInstance<McpSchema.TextContent>().joinToString("\n") { it.text() }
        val structured = structured(result.structuredContent()) ?: structured(text)
        val ok = (structured?.get("ok") as? Boolean) ?: (result.isError() != true)
        val code = structured?.get("code")?.toString() ?: if (ok) "OK" else ErrorCode.UPSTREAM_FAILED.name
        val message = structured?.get("message")?.toString() ?: if (ok) "성공" else "툴이 실패를 돌려주었습니다."
        val approvalId = (structured?.get("approvalId") as? Number)?.toLong()
        val content = text.ifBlank { jsonMapper.writeValueAsString(structured ?: mapOf("ok" to ok, "code" to code, "message" to message)) }
        return ToolCallOutcome(ok, code, message, approvalId, content)
    }

    private fun structured(value: Any?): Map<*, *>? = when (value) {
        null -> null
        is Map<*, *> -> value
        is String -> if (value.isBlank()) null else try {
            jsonMapper.readValue(value, Map::class.java)
        } catch (e: RuntimeException) {
            null
        }
        else -> try {
            jsonMapper.convertValue(value, Map::class.java)
        } catch (e: RuntimeException) {
            null
        }
    }

    companion object {
        const val MAX_TOOLS = 200
    }
}
