package com.sleekydz86.aibackoffice.gateway.filter

import com.sleekydz86.aibackoffice.common.Headers
import com.sleekydz86.aibackoffice.gateway.error.GatewayTraceIds
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val traceId = GatewayTraceIds.sanitize(exchange.request.headers.getFirst(Headers.TRACE_ID))
        val request = exchange.request.mutate().headers { it.set(Headers.TRACE_ID, traceId) }.build()
        val mutated = exchange.mutate().request(request).build()
        mutated.attributes[GatewayTraceIds.ATTRIBUTE] = traceId
        mutated.response.headers.set(Headers.TRACE_ID, traceId)
        return chain.filter(mutated)
    }
}
