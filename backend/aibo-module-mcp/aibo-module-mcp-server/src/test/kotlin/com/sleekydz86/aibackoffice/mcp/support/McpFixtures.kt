package com.sleekydz86.aibackoffice.mcp.support

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.mcp.application.ToolPolicySource
import com.sleekydz86.aibackoffice.mcp.application.tool.McpToolHandler
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolArguments
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolCall
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolDefinition
import com.sleekydz86.aibackoffice.mcp.application.tool.ToolOutput
import com.sleekydz86.aibackoffice.mcp.domain.ToolPolicy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

object McpFixtures {

    val jsonMapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    fun user(
        id: String = "7",
        perms: Set<String>,
        scopes: Set<String> = setOf("TVN"),
        actorType: ActorType = ActorType.AGENT,
    ) = LoginUser(id, "사용자$id", null, emptySet(), perms, scopes, 0, "jti-$id", TokenType.USER, null, actorType, "user-token")

    fun delegated(onBehalfOf: String = "7", perms: Set<String>, scopes: Set<String> = setOf("TVN")) =
        LoginUser("svc:governance", "governance", null, emptySet(), perms, scopes, 0, "jti-svc", TokenType.SERVICE, onBehalfOf, ActorType.SYSTEM, "service-token")

    fun policy(name: String, risk: RiskLevel, permission: String, enabled: Boolean = true, domain: String = "schedule") =
        ToolPolicy(name, domain, risk, permission, enabled, if (risk == RiskLevel.WRITE_HIGH) 1440 else 0)

    fun policies(vararg values: ToolPolicy): ToolPolicySource = object : ToolPolicySource {
        override fun all(): List<ToolPolicy> = values.toList()
    }

    class FakeTool(
        name: String,
        private val channelArgument: Boolean = false,
        private val output: (ToolCall) -> ToolOutput = { ToolOutput(mapOf("done" to true)) },
    ) : McpToolHandler {
        val calls = mutableListOf<ToolCall>()
        val prechecks = mutableListOf<ToolCall>()

        override val definition = ToolDefinition(name, name, "$name 설명", mapOf("type" to "object"))

        override fun scopeChannel(arguments: ToolArguments): String? = if (channelArgument) arguments.channel() else null

        override fun precheck(call: ToolCall) {
            prechecks += call
        }

        override fun execute(call: ToolCall): ToolOutput {
            calls += call
            return output(call)
        }
    }
}
