package com.sleekydz86.aibackoffice.framework.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aibo.security")
data class AiboSecurityProperties(
    val jwkSetUri: String = "",
    val issuer: String = "aibo-auth",
    val permitAllUrls: List<String> = emptyList(),
)
