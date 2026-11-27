package com.sleekydz86.aibackoffice.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aibo.gateway")
data class GatewayProperties(
    val jwkSetUri: String = "",
    val issuer: String = "aibo-auth",
    val contentMetaRouteId: String = "content-meta",
    val contentMetaAdminToken: String = "",
    val publicPaths: List<String> = listOf("/api/auth/login", "/api/auth/refresh", "/actuator/health/**"),
)
