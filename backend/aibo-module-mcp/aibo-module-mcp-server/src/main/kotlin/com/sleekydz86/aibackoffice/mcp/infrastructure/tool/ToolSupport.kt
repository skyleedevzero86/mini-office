package com.sleekydz86.aibackoffice.mcp.infrastructure.tool

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.mcp.application.tool.McpToolHandler
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolDefinition
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolList
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolOutput
import org.springframework.http.ResponseEntity

abstract class BaseTool(
    name: String,
    title: String,
    description: String,
    inputSchema: Map<String, Any?>,
) : McpToolHandler {
    override val definition: ToolDefinition = ToolDefinition(name, title, description, inputSchema)
}

object ToolResponses {

    fun <T> list(response: ResponseEntity<List<T>>): ToolList<T> {
        val items = response.body.orEmpty()
        val total = response.headers.getFirst(Headers.TOTAL_COUNT)?.toLongOrNull() ?: items.size.toLong()
        return ToolList.of(items, total)
    }

    fun <T> list(items: List<T>): ToolList<T> = ToolList.of(items, items.size.toLong())

    fun summary(list: ToolList<*>): Map<String, Any?> =
        mapOf("total" to list.total, "shown" to list.items.size, "truncated" to list.truncated)

    fun listed(list: ToolList<*>, message: String): ToolOutput =
        ToolOutput(data = list, message = message)

    fun listedUntrusted(list: ToolList<*>, message: String): ToolOutput =
        ToolOutput(data = summary(list), untrusted = list.items, message = message)

    fun pick(source: Map<String, Any?>, vararg keys: String): Map<String, Any?> =
        keys.associateWith { source[it] }

    fun without(source: Map<String, Any?>, vararg keys: String): Map<String, Any?> =
        source.filterKeys { it !in keys }
}
