package com.sleekydz86.aibackoffice.mcp.infrastructure.redis

import com.sleekydz86.aibackoffice.framework.redis.core.RedisKeys
import com.sleekydz86.aibackoffice.mcp.application.ExecutionLedger
import com.sleekydz86.aibackoffice.mcp.application.LedgerEntry
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisExecutionLedger(
    private val redis: StringRedisTemplate,
    private val properties: McpProperties,
) : ExecutionLedger {

    override fun begin(approvalId: Long): LedgerEntry {
        val key = RedisKeys.approvalExecution(approvalId)
        val claimed = redis.opsForValue().setIfAbsent(key, RUNNING, properties.executionLockTtl)
        if (claimed == true) return LedgerEntry.Started
        val current = redis.opsForValue().get(key) ?: return LedgerEntry.Running
        return if (current == RUNNING) LedgerEntry.Running else LedgerEntry.Done(current)
    }

    override fun finish(approvalId: Long, resultJson: String) {
        redis.opsForValue().set(RedisKeys.approvalExecution(approvalId), resultJson, properties.executionResultTtl)
    }

    companion object {
        private const val RUNNING = "RUNNING"
    }
}
