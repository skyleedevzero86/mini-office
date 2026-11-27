package com.sleekydz86.aibackoffice.framework.audit.config

import com.sleekydz86.aibackoffice.framework.audit.core.AuditOutboxRelay
import com.sleekydz86.aibackoffice.framework.audit.core.AuditOutboxStore
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.audit.core.AuditSink
import com.sleekydz86.aibackoffice.framework.audit.core.HttpAuditSink
import com.sleekydz86.aibackoffice.framework.audit.core.ServiceTokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

@AutoConfiguration(afterName = ["org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"])
@EnableScheduling
@EnableConfigurationProperties(AiboAuditProperties::class)
class AiboAuditAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate::class)
    fun auditOutboxStore(jdbc: JdbcTemplate, transactionManager: PlatformTransactionManager): AuditOutboxStore =
        AuditOutboxStore(jdbc, TransactionTemplate(transactionManager))

    @Bean
    @ConditionalOnBean(AuditOutboxStore::class)
    fun auditRecorder(
        store: AuditOutboxStore,
        jsonMapper: JsonMapper,
        @Value("\${spring.application.name:aibo}") serviceName: String,
    ): AuditRecorder = AuditRecorder(store, jsonMapper, serviceName.removePrefix("aibo-"))

    @Bean
    @ConditionalOnMissingBean
    fun serviceTokenProvider(builder: RestClient.Builder, properties: AiboAuditProperties): ServiceTokenProvider =
        ServiceTokenProvider(builder.build(), properties)

    @Bean
    @ConditionalOnMissingBean
    fun auditSink(builder: RestClient.Builder, properties: AiboAuditProperties, tokens: ServiceTokenProvider): AuditSink =
        HttpAuditSink(builder.build(), properties, tokens)

    @Bean
    @ConditionalOnBean(AuditOutboxStore::class)
    fun auditOutboxRelay(store: AuditOutboxStore, sink: AuditSink, properties: AiboAuditProperties): AuditOutboxRelay =
        AuditOutboxRelay(store, sink, properties)
}
