package com.sleekydz86.aibackoffice.gateway.error

import com.sleekydz86.aibackoffice.common.ErrorCode
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.support.NotFoundException
import org.springframework.cloud.gateway.support.TimeoutException as GatewayTimeoutException
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

@Component
@Order(-2)
class GatewayErrorHandler(private val jsonMapper: JsonMapper) : WebExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val path = exchange.request.path.value()
        val code = classify(ex)
        when {
            code == ErrorCode.UPSTREAM_FAILED ->
                log.warn("뒤쪽 서비스에 연결하지 못했습니다. 경로={} 추적 번호={} 이유={}", path, GatewayTraceIds.of(exchange), ex.message)
            code.status >= 500 ->
                log.error("gateway 처리 중 오류가 났습니다. 경로={} 추적 번호={}", path, GatewayTraceIds.of(exchange), ex)
        }
        return GatewayErrors.write(exchange, jsonMapper, code)
    }

    companion object {
        fun classify(ex: Throwable): ErrorCode {
            if (causes(ex).any { it is ConnectException || it is UnknownHostException || it is TimeoutException || it is GatewayTimeoutException }) {
                return ErrorCode.UPSTREAM_FAILED
            }
            if (ex is NotFoundException) return ErrorCode.UPSTREAM_FAILED
            if (ex is ResponseStatusException) {
                return when (ex.statusCode.value()) {
                    400 -> ErrorCode.VALIDATION_FAILED
                    401 -> ErrorCode.UNAUTHORIZED
                    403 -> ErrorCode.PERMISSION_DENIED
                    404, 405 -> ErrorCode.NOT_FOUND
                    429 -> ErrorCode.TOO_MANY_REQUESTS
                    502, 503, 504 -> ErrorCode.UPSTREAM_FAILED
                    else -> if (ex.statusCode.is4xxClientError) ErrorCode.VALIDATION_FAILED else ErrorCode.INTERNAL_ERROR
                }
            }
            return ErrorCode.INTERNAL_ERROR
        }

        private fun causes(ex: Throwable): Sequence<Throwable> = generateSequence(ex) { it.cause?.takeIf { cause -> cause !== it } }.take(10)
    }
}
