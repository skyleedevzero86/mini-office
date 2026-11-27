package com.sleekydz86.aibackoffice.framework.audit.core

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.audit.config.AiboAuditProperties
import java.time.Instant
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

fun interface AuditSink {
    fun send(payloads: List<String>)
}

class HttpAuditSink(
    private val restClient: RestClient,
    private val properties: AiboAuditProperties,
    private val tokens: ServiceTokenProvider,
) : AuditSink {

    override fun send(payloads: List<String>) {
        if (properties.governanceUrl.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "감사 기록을 보낼 주소 aibo.audit.governance-url 이 비어 있습니다.")
        }
        restClient.post()
            .uri(properties.governanceUrl.trimEnd('/') + INGEST_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.token()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(payloads.joinToString(prefix = "[", postfix = "]", separator = ","))
            .retrieve()
            .toBodilessEntity()
    }

    companion object {
        const val INGEST_PATH = "/internal/governance/audit-events"
    }
}

class ServiceTokenProvider(
    private val restClient: RestClient,
    private val properties: AiboAuditProperties,
) {
    @Volatile
    private var cached: Pair<String, Instant>? = null

    @Synchronized
    fun token(): String {
        cached?.takeIf { it.second.isAfter(Instant.now().plusSeconds(30)) }?.let { return it.first }
        if (properties.authUrl.isBlank() || properties.clientId.isBlank() || properties.clientSecret.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "서비스 토큰을 받을 설정 aibo.audit.auth-url, client-id, client-secret 이 비어 있습니다.")
        }
        val response = restClient.post()
            .uri(properties.authUrl.trimEnd('/') + "/internal/auth/service-tokens")
            .header("X-Client-Id", properties.clientId)
            .header("X-Client-Secret", properties.clientSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("onBehalfOf" to null, "purpose" to "audit-relay"))
            .retrieve()
            .body(TokenBody::class.java)
            ?: throw BusinessException(ErrorCode.UPSTREAM_FAILED, "서비스 토큰 응답이 비어 있습니다.")
        cached = response.accessToken to Instant.now().plusSeconds(response.expiresIn)
        return response.accessToken
    }

    data class TokenBody(val accessToken: String, val expiresIn: Long)
}
