package com.sleekydz86.aibackoffice.mcp.infrastructure.redis

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.framework.redis.core.RedisKeys
import com.sleekydz86.aibackoffice.governance.api.GovernanceInternalApi
import com.sleekydz86.aibackoffice.mcp.application.ToolPolicySource
import com.sleekydz86.aibackoffice.mcp.domain.ToolPolicy
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

@Component
class RedisToolPolicySource(
    private val redis: StringRedisTemplate,
    private val governance: GovernanceInternalApi,
    private val properties: McpProperties,
    private val jsonMapper: JsonMapper,
) : ToolPolicySource {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun all(): List<ToolPolicy> {
        cached()?.let { return it }
        val fresh = fetch()
        try {
            redis.opsForValue().set(RedisKeys.toolPolicies(), jsonMapper.writeValueAsString(fresh), properties.policyCacheTtl)
        } catch (exception: RuntimeException) {
            log.warn("툴 정책을 Redis 에 넣지 못했습니다. 다음 요청에서 다시 읽습니다. 이유={}", exception.message)
        }
        return fresh
    }

    private fun cached(): List<ToolPolicy>? = try {
        redis.opsForValue().get(RedisKeys.toolPolicies())?.let { jsonMapper.readValue(it, LIST_TYPE) }
    } catch (exception: RuntimeException) {
        log.warn("Redis 에서 툴 정책을 읽지 못해 governance 에서 바로 읽습니다. 이유={}", exception.message)
        null
    }

    private fun fetch(): List<ToolPolicy> {
        if (properties.governanceUrl.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "governance 주소 aibo.mcp.governance-url 이 비어 있습니다.")
        }
        val policies = try {
            governance.toolPolicies()
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: RuntimeException) {
            log.warn("governance 에서 툴 정책을 읽지 못했습니다. 이유={}", exception.message)
            throw BusinessException(ErrorCode.UPSTREAM_FAILED, "툴 정책을 읽지 못해 툴을 열 수 없습니다.")
        }
        return policies.mapNotNull { policy ->
            val risk = RiskLevel.entries.firstOrNull { it.name == policy.riskLevel }
            if (risk == null) {
                log.warn("알 수 없는 위험도라 툴을 닫아 둡니다. 툴={} 위험도={}", policy.toolName, policy.riskLevel)
                return@mapNotNull null
            }
            ToolPolicy(policy.toolName, policy.domain, risk, policy.requiredPermission, policy.enabled, policy.approvalTtlMinutes)
        }
    }

    companion object {
        private val LIST_TYPE = object : TypeReference<List<ToolPolicy>>() {}
    }
}
