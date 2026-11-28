package com.sleekydz86.aibackoffice.mcp.infrastructure.config

import com.sleekydz86.aibackoffice.auth.api.AuthInternalApi
import com.sleekydz86.aibackoffice.framework.audit.core.ServiceTokenProvider
import com.sleekydz86.aibackoffice.governance.api.GovernanceInternalApi
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@ConfigurationProperties("aibo.mcp")
data class McpProperties(
    val authUrl: String = "",
    val governanceUrl: String = "",
    val clientId: String = "mcp",
    val clientSecret: String = "",
    val endpoint: String = "/mcp",
    val policyCacheTtl: Duration = Duration.ofSeconds(60),
    val executionLockTtl: Duration = Duration.ofMinutes(15),
    val executionResultTtl: Duration = Duration.ofDays(7),
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(30),
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpProperties::class)
class McpConfiguration {

    @Bean
    fun downstreamRestClient(builder: RestClient.Builder, properties: McpProperties): RestClient {
        val http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
        val factory = JdkClientHttpRequestFactory(http).apply { setReadTimeout(properties.readTimeout) }
        return builder.clone().requestFactory(factory).build()
    }

    @Bean
    fun authInternalApi(downstreamRestClient: RestClient, properties: McpProperties): AuthInternalApi =
        proxy(downstreamRestClient.mutate().baseUrl(properties.authUrl.trimEnd('/')).build(), AuthInternalApi::class.java)

    @Bean
    fun serviceGovernanceApi(
        downstreamRestClient: RestClient,
        properties: McpProperties,
        tokens: ServiceTokenProvider,
    ): GovernanceInternalApi {
        val client = downstreamRestClient.mutate()
            .baseUrl(properties.governanceUrl.trimEnd('/'))
            .requestInterceptor { request, body, execution ->
                request.headers.setBearerAuth(tokens.token())
                execution.execute(request, body)
            }
            .build()
        return proxy(client, GovernanceInternalApi::class.java)
    }

    companion object {
        fun <T : Any> proxy(client: RestClient, type: Class<T>): T =
            HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(type)
    }
}
