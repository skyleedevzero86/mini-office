package com.sleekydz86.aibackoffice.gateway.error

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.ErrorResponse
import com.sleekydz86.aibackoffice.common.Headers
import java.util.UUID
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

object GatewayTraceIds {

    const val ATTRIBUTE = "aibo.traceId"
    private val SAFE = Regex("[A-Za-z0-9-]{8,64}")

    fun sanitize(given: String?): String = given?.takeIf { SAFE.matches(it) } ?: newId()

    fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    fun of(exchange: ServerWebExchange): String =
        exchange.getAttribute<String>(ATTRIBUTE) ?: sanitize(exchange.request.headers.getFirst(Headers.TRACE_ID))
}

object GatewayErrors {

    fun write(exchange: ServerWebExchange, jsonMapper: JsonMapper, code: ErrorCode, message: String = code.defaultMessage): Mono<Void> {
        val response = exchange.response
        if (response.isCommitted) return Mono.empty()
        val traceId = GatewayTraceIds.of(exchange)
        response.statusCode = HttpStatusCode.valueOf(code.status)
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.set(Headers.TRACE_ID, traceId)
        val bytes = jsonMapper.writeValueAsBytes(ErrorResponse(code.name, message, traceId))
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
    }
}
