package com.sleekydz86.aibackoffice.gateway.filter

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.ErrorResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class InternalPathBlockFilter(private val jsonMapper: JsonMapper) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!path.startsWith("/internal/") && path != "/internal") return chain.filter(exchange)
        val response = exchange.response
        response.statusCode = HttpStatus.NOT_FOUND
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = jsonMapper.writeValueAsBytes(ErrorResponse(ErrorCode.NOT_FOUND.name, ErrorCode.NOT_FOUND.defaultMessage, null))
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)))
    }
}
