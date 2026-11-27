package com.sleekydz86.aibackoffice.governance.infrastructure.config

import com.sleekydz86.aibackoffice.auth.api.AuthInternalApi
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.governance.application.GovernanceViews
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import tools.jackson.databind.json.JsonMapper

@ConfigurationProperties("aibo.governance")
data class GovernanceProperties(
    val authUrl: String = "",
    val mcpUrl: String = "",
    val clientId: String = "governance",
    val clientSecret: String = "",
    val defaultApprovalTtlMinutes: Int = 1440,
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(30),
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GovernanceProperties::class)
class GovernanceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Zones.SEOUL_CLOCK

    @Bean
    fun governanceViews(jsonMapper: JsonMapper): GovernanceViews = GovernanceViews(jsonMapper)

    @Bean
    fun downstreamRestClient(builder: RestClient.Builder, properties: GovernanceProperties): RestClient {
        val http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
        val factory = JdkClientHttpRequestFactory(http).apply { setReadTimeout(properties.readTimeout) }
        return builder.clone().requestFactory(factory).build()
    }

    @Bean
    fun authInternalApi(downstreamRestClient: RestClient, properties: GovernanceProperties): AuthInternalApi {
        val client = downstreamRestClient.mutate().baseUrl(properties.authUrl.trimEnd('/')).build()
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build()
            .createClient(AuthInternalApi::class.java)
    }
}
