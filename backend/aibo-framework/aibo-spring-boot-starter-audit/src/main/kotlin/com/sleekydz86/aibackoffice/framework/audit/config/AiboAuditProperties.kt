package com.sleekydz86.aibackoffice.framework.audit.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aibo.audit")
data class AiboAuditProperties(
    val relayEnabled: Boolean = true,
    val relayDelay: Duration = Duration.ofSeconds(3),
    val batchSize: Int = 100,
    val maxRetries: Int = 10,
    val lease: Duration = Duration.ofSeconds(60),
    val governanceUrl: String = "",
    val authUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
)
