package com.sleekydz86.aibackoffice.mcp.application

import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolDefinition
import com.sleekydz86.aibackoffice.mcp.domain.AccessDecision
import com.sleekydz86.aibackoffice.mcp.domain.ToolAccess
import org.springframework.stereotype.Service

data class VisibleTool(
    val name: String,
    val title: String,
    val description: String,
    val domain: String,
    val riskLevel: String,
    val requiredPermission: String,
    val needsApproval: Boolean,
    val inputSchema: Map<String, Any?>,
) {
    fun definition(): ToolDefinition = ToolDefinition(name, title, description, inputSchema)
}

@Service
class ToolVisibilityService(
    private val catalog: ToolCatalog,
    private val policies: ToolPolicySource,
) {

    fun visible(user: LoginUser): List<VisibleTool> {
        if (user.service) return emptyList()
        val policyByName = policies.all().associateBy { it.toolName }
        return catalog.all().mapNotNull { handler ->
            val definition = handler.definition
            val policy = policyByName[definition.name]
            val decision = ToolAccess.decide(user, definition.name, true, policy)
            if (decision != AccessDecision.Allowed || policy == null) return@mapNotNull null
            VisibleTool(
                name = definition.name,
                title = definition.title,
                description = definition.description,
                domain = policy.domain,
                riskLevel = policy.riskLevel.name,
                requiredPermission = policy.requiredPermission,
                needsApproval = policy.needsApproval,
                inputSchema = definition.inputSchema,
            )
        }
    }
}
