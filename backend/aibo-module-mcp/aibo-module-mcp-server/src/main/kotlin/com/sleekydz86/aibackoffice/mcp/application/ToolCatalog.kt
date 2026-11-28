package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.mcp.application.tool.McpToolHandler
import com.sleekydz86.aibackoffice.mcp.domain.ToolAccess
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ToolCatalog(handlers: List<McpToolHandler>) {

    @Autowired
    constructor(provider: ObjectProvider<McpToolHandler>) : this(provider.orderedStream().toList())

    private val byName: Map<String, McpToolHandler>

    init {
        val duplicated = handlers.groupBy { it.definition.name }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "같은 이름의 툴이 두 번 등록되었습니다: ${duplicated.joinToString(", ")}" }
        val forbidden = handlers.map { it.definition.name }.filter { it in ToolAccess.NEVER_EXPOSED }
        require(forbidden.isEmpty()) { "에이전트에게 주면 안 되는 툴이 등록되었습니다: ${forbidden.joinToString(", ")}" }
        byName = handlers.associateBy { it.definition.name }
    }

    fun find(toolName: String): McpToolHandler? = byName[toolName]

    fun all(): List<McpToolHandler> = byName.values.sortedBy { it.definition.name }
}
