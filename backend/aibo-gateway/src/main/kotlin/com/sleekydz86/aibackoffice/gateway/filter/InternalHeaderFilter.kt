package com.sleekydz86.aibackoffice.gateway.filter

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.gateway.config.GatewayProperties
import com.sleekydz86.aibackoffice.gateway.error.GatewayTraceIds
import java.net.URLEncoder
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class InternalHeaderFilter(private val properties: GatewayProperties) : GlobalFilter, Ordered {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val route = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
        val traceId = GatewayTraceIds.of(exchange)
        val toContentMeta = route?.id == properties.contentMetaRouteId
        return ReactiveSecurityContextHolder.getContext()
            .mapNotNull { it.authentication?.principal as? Jwt }
            .map { it.getClaimAsString("name") ?: it.subject ?: "" }
            .defaultIfEmpty("")
            .flatMap { name ->
                val request = exchange.request.mutate().headers { headers ->
                    headers.remove(ADMIN_TOKEN_HEADER)
                    headers.remove(WORKER_HEADER)
                    headers.remove(Headers.ACTOR_TYPE)
                    headers.set(Headers.TRACE_ID, traceId)
                    if (toContentMeta) {
                        headers.remove(HttpHeaders.AUTHORIZATION)
                        if (properties.contentMetaAdminToken.isBlank()) {
                            log.warn("content-meta 운영 토큰이 비어 있어 운영 API 호출은 거절됩니다. 환경변수 CONTENT_META_ADMIN_TOKEN 을 확인하세요.")
                        } else {
                            headers.set(ADMIN_TOKEN_HEADER, properties.contentMetaAdminToken)
                        }
                        if (name.isNotBlank()) headers.set(WORKER_HEADER, URLEncoder.encode(name, Charsets.UTF_8))
                    }
                }.build()
                exchange.response.headers.set(Headers.TRACE_ID, traceId)
                chain.filter(exchange.mutate().request(request).build())
            }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 100

    companion object {
        const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
        const val WORKER_HEADER = "X-Worker"
    }
}
