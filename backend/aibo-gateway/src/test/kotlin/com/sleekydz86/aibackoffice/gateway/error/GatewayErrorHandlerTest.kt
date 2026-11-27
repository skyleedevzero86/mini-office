package com.sleekydz86.aibackoffice.gateway.error

import com.sleekydz86.aibackoffice.common.ErrorCode
import java.net.ConnectException
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class GatewayErrorHandlerTest {

    @Test
    @DisplayName("연결 거부와 시간 초과는 감싼 예외 안에 있어도 연동 실패로 본다")
    fun connectionProblemsAreUpstreamFailures() {
        // then
        assertEquals(ErrorCode.UPSTREAM_FAILED, GatewayErrorHandler.classify(IllegalStateException("감쌈", ConnectException("거부"))))
        assertEquals(ErrorCode.UPSTREAM_FAILED, GatewayErrorHandler.classify(RuntimeException(TimeoutException("초과"))))
        assertEquals(ErrorCode.UPSTREAM_FAILED, GatewayErrorHandler.classify(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)))
    }

    @Test
    @DisplayName("상태 코드가 있는 오류는 공통 오류 코드로 바꾸고 나머지는 서버 오류로 본다")
    fun statusErrorsAreMapped() {
        // then
        assertEquals(ErrorCode.NOT_FOUND, GatewayErrorHandler.classify(ResponseStatusException(HttpStatus.NOT_FOUND)))
        assertEquals(ErrorCode.VALIDATION_FAILED, GatewayErrorHandler.classify(ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE)))
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, GatewayErrorHandler.classify(ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)))
        assertEquals(ErrorCode.INTERNAL_ERROR, GatewayErrorHandler.classify(IllegalArgumentException("모름")))
    }
}
