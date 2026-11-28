package com.sleekydz86.aibackoffice.agent.infrastructure.config

import com.sleekydz86.aibackoffice.agent.application.AgentLimits
import com.sleekydz86.aibackoffice.agent.application.AgentLoop
import com.sleekydz86.aibackoffice.agent.application.ConversationService
import com.sleekydz86.aibackoffice.agent.application.ConversationStore
import com.sleekydz86.aibackoffice.agent.application.LlmClient
import com.sleekydz86.aibackoffice.agent.application.PromptStore
import com.sleekydz86.aibackoffice.agent.application.RequestLimiter
import com.sleekydz86.aibackoffice.agent.application.ToolGateway
import com.sleekydz86.aibackoffice.agent.infrastructure.llm.FakeLlmClient
import com.sleekydz86.aibackoffice.agent.infrastructure.llm.SpringAiLlmClient
import com.sleekydz86.aibackoffice.agent.infrastructure.mcp.McpToolGateway
import com.sleekydz86.aibackoffice.framework.redis.core.RedisRateLimiter
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@ConfigurationProperties("aibo.agent")
data class AgentProperties(
    val provider: String = FakeLlmClient.MODEL,
    val mcpUrl: String = "",
    val mcpEndpoint: String = "/mcp",
    val mcpTimeout: Duration = Duration.ofSeconds(30),
    val llmTimeout: Duration = Duration.ofSeconds(90),
    val streamTimeout: Duration = Duration.ofMinutes(5),
    val promptName: String = "ops-agent",
    val maxToolCalls: Int = 8,
    val historyMessages: Int = 20,
    val maxMessageChars: Int = 4000,
    val maxToolResultChars: Int = 8000,
    val rateLimit: Int = 20,
    val rateWindow: Duration = Duration.ofMinutes(1),
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentProperties::class)
class AgentConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun llmClient(properties: AgentProperties, chatModels: ObjectProvider<ChatModel>, jsonMapper: JsonMapper): LlmClient {
        val provider = properties.provider.trim().lowercase()
        if (provider == FakeLlmClient.MODEL) {
            log.info("에이전트가 연습용 가짜 모델로 뜹니다.")
            return FakeLlmClient(jsonMapper)
        }
        val chatModel = chatModels.ifAvailable
            ?: throw IllegalStateException("모델 공급자 $provider 설정을 찾지 못했습니다. AGENT_LLM_PROVIDER 와 키 환경변수를 확인하세요.")
        log.info("에이전트가 모델 공급자 {} 로 뜹니다.", provider)
        return SpringAiLlmClient(chatModel, provider, properties.llmTimeout)
    }

    @Bean
    fun toolGateway(properties: AgentProperties, jsonMapper: JsonMapper): ToolGateway {
        if (properties.mcpUrl.isBlank()) throw IllegalStateException("MCP_URL 환경변수가 비어 있습니다.")
        return McpToolGateway(properties.mcpUrl, properties.mcpEndpoint, properties.mcpTimeout, jsonMapper)
    }

    @Bean
    fun requestLimiter(properties: AgentProperties, rateLimiter: RedisRateLimiter): RequestLimiter = RequestLimiter { userId ->
        rateLimiter.acquireOrThrow(RATE_NAME, userId, properties.rateLimit, properties.rateWindow)
    }

    @Bean
    fun agentLoop(
        llm: LlmClient,
        tools: ToolGateway,
        store: ConversationStore,
        jsonMapper: JsonMapper,
        properties: AgentProperties,
    ): AgentLoop = AgentLoop(
        llm,
        tools,
        store,
        jsonMapper,
        AgentLimits(properties.maxToolCalls, properties.historyMessages, properties.maxToolResultChars),
    )

    @Bean
    fun conversationService(
        store: ConversationStore,
        prompts: PromptStore,
        limiter: RequestLimiter,
        tools: ToolGateway,
        jsonMapper: JsonMapper,
        properties: AgentProperties,
    ): ConversationService = ConversationService(store, prompts, limiter, tools, jsonMapper, properties.promptName, properties.maxMessageChars)

    @Bean(destroyMethod = "close")
    fun agentExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    companion object {
        const val RATE_NAME = "agent-message"
    }
}
