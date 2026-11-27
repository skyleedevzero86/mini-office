package com.sleekydz86.aibackoffice.auth.infrastructure.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aibo.auth")
data class AuthProperties(
    val issuer: String = "aibo-auth",
    val keyId: String = "aibo-auth-1",
    val privateKeyPem: String = "",
    val publicKeyPem: String = "",
    val allowEphemeralKey: Boolean = false,
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(7),
    val serviceTokenTtl: Duration = Duration.ofMinutes(5),
    val loginFailureLimit: Int = 5,
    val loginLockDuration: Duration = Duration.ofMinutes(10),
    val seedPassword: String = "",
    val serviceClients: Map<String, String> = emptyMap(),
)
