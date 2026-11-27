package com.sleekydz86.aibackoffice.governance.infrastructure.persistence

import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.governance.domain.ToolPolicy
import java.sql.ResultSet
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class ToolPolicyStore(private val jdbc: JdbcTemplate) {

    fun findAll(): List<ToolPolicy> = jdbc.query("$SELECT ORDER BY domain, tool_name", ::map)

    fun find(toolName: String): ToolPolicy? = jdbc.query("$SELECT WHERE tool_name = ?", ::map, toolName).firstOrNull()

    fun findForUpdate(toolName: String): ToolPolicy? =
        jdbc.query("$SELECT WHERE tool_name = ? FOR UPDATE", ::map, toolName).firstOrNull()

    fun update(policy: ToolPolicy) {
        jdbc.update(
            "UPDATE tool_policies SET risk_level = ?, required_permission = ?, enabled = ?, approval_ttl_minutes = ?, updated_at = ? " +
                "WHERE tool_name = ?",
            policy.riskLevel.name,
            policy.requiredPermission,
            policy.enabled,
            policy.approvalTtlMinutes,
            policy.updatedAt,
            policy.toolName,
        )
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): ToolPolicy = ToolPolicy(
        toolName = rs.getString("tool_name"),
        domain = rs.getString("domain"),
        riskLevel = RiskLevel.valueOf(rs.getString("risk_level")),
        requiredPermission = rs.getString("required_permission"),
        enabled = rs.getBoolean("enabled"),
        approvalTtlMinutes = rs.getInt("approval_ttl_minutes"),
        updatedAt = rs.getObject("updated_at", LocalDateTime::class.java),
    )

    companion object {
        private const val SELECT =
            "SELECT tool_name, domain, risk_level, required_permission, enabled, approval_ttl_minutes, updated_at FROM tool_policies "
    }
}
