package com.sleekydz86.aibackoffice.gateway.filter

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.gateway.error.GatewayErrors
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class InternalPathBlockFilter(private val jsonMapper: JsonMapper) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!path.startsWith("/internal/") && path != "/internal") return chain.filter(exchange)
        return GatewayErrors.write(exchange, jsonMapper, ErrorCode.NOT_FOUND)
    }
}
