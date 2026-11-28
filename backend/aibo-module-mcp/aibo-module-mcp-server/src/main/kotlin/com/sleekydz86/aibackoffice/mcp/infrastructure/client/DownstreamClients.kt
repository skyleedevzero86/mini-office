package com.sleekydz86.aibackoffice.mcp.infrastructure.client

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.governance.api.GovernanceApi
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpConfiguration
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpProperties
import com.sleekydz86.aibackoffice.operation.api.OperationApi
import java.net.URLEncoder
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class DownstreamClients(
    private val downstreamRestClient: RestClient,
    private val properties: McpProperties,
) {

    private val contentMeta: ContentMetaApi by lazy {
        val client = downstreamRestClient.mutate()
            .baseUrl(properties.contentMetaUrl.trimEnd('/'))
            .requestInterceptor { request, body, execution ->
                request.headers.remove(HttpHeaders.AUTHORIZATION)
                request.headers.set(ContentMetaApi.ADMIN_TOKEN, properties.contentMetaAdminToken)
                execution.execute(request, body)
            }
            .build()
        McpConfiguration.proxy(client, ContentMetaApi::class.java)
    }

    fun operation(user: LoginUser): OperationApi =
        McpConfiguration.proxy(forUser(properties.operationUrl, "operation", user), OperationApi::class.java)

    fun governance(user: LoginUser): GovernanceApi =
        McpConfiguration.proxy(forUser(properties.governanceUrl, "governance", user), GovernanceApi::class.java)

    fun contentMeta(): ContentMetaApi {
        if (properties.contentMetaUrl.isBlank() || properties.contentMetaAdminToken.isBlank()) {
            throw BusinessException(
                ErrorCode.SERVICE_NOT_CONFIGURED,
                "content-meta 주소나 운영 토큰이 비어 있습니다. 환경변수 CONTENT_META_URL 과 CONTENT_META_ADMIN_TOKEN 을 확인하세요.",
            )
        }
        return contentMeta
    }

    fun worker(user: LoginUser): String {
        val actor = if (user.service) "승인실행" else if (user.actorType == ActorType.AGENT) "에이전트" else "사람"
        val who = user.onBehalfOf ?: user.userId
        return URLEncoder.encode("$actor $who ${user.name}".take(MAX_WORKER), Charsets.UTF_8)
    }

    private fun forUser(baseUrl: String, service: String, user: LoginUser): RestClient {
        if (baseUrl.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "$service 주소 설정이 비어 있습니다.")
        }
        val builder = downstreamRestClient.mutate()
            .baseUrl(baseUrl.trimEnd('/'))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${user.rawToken}")
        if (!user.service && user.actorType == ActorType.AGENT) builder.defaultHeader(Headers.ACTOR_TYPE, ActorType.AGENT.name)
        return builder.build()
    }

    companion object {
        private const val MAX_WORKER = 50
    }
}
