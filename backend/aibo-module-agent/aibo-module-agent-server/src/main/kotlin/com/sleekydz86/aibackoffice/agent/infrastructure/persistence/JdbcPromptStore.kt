package com.sleekydz86.aibackoffice.agent.infrastructure.persistence

import com.sleekydz86.aibackoffice.agent.application.PromptStore
import com.sleekydz86.aibackoffice.agent.domain.PromptTemplate
import java.sql.ResultSet
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class JdbcPromptStore(private val jdbc: JdbcTemplate) : PromptStore {

    override fun active(name: String): PromptTemplate? =
        jdbc.query("$SELECT WHERE name = ? AND active = 1 ORDER BY version DESC LIMIT 1", ::map, name).firstOrNull()

    override fun find(id: Long): PromptTemplate? = jdbc.query("$SELECT WHERE id = ?", ::map, id).firstOrNull()

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = PromptTemplate(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        version = rs.getInt("version"),
        systemPrompt = rs.getString("system_prompt"),
    )

    companion object {
        private const val SELECT = "SELECT id, name, version, system_prompt FROM prompt_templates"
    }
}
