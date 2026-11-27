package com.sleekydz86.aibackoffice.gateway

import com.sleekydz86.aibackoffice.common.Headers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "aibo.gateway.jwk-set-uri=http://127.0.0.1:1/oauth2/jwks",
        "aibo.gateway.issuer=aibo-auth",
        "spring.cloud.gateway.server.webflux.routes[0].id=auth",
        "spring.cloud.gateway.server.webflux.routes[0].uri=http://127.0.0.1:1",
        "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/auth/**",
    ],
)
class GatewayErrorResponseTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val jsonMapper = JsonMapper.builder().build()

    private fun client(): WebTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    private fun body(bytes: ByteArray?): Map<*, *> = jsonMapper.readValue(bytes, Map::class.java)

    @Test
    @DisplayName("뒤쪽 서비스가 꺼져 있으면 500 대신 502 UPSTREAM_FAILED 와 추적 번호를 돌려준다")
    fun upstreamDownIsBadGateway() {
        // when
        val result = client().post().uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody().returnResult()

        // then
        val body = body(result.responseBody)
        assertEquals("UPSTREAM_FAILED", body["code"])
        assertNotNull(body["traceId"])
        assertEquals(body["traceId"], result.responseHeaders.getFirst(Headers.TRACE_ID))
    }

    @Test
    @DisplayName("토큰 없이 부르면 401 오류에도 추적 번호를 채운다")
    fun unauthorizedHasTraceId() {
        // when
        val result = client().get().uri("/api/operation/channels")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody().returnResult()

        // then
        val body = body(result.responseBody)
        assertEquals("UNAUTHORIZED", body["code"])
        assertNotNull(body["traceId"])
    }

    @Test
    @DisplayName("내부 주소는 404 로 막고 보낸 추적 번호를 그대로 돌려준다")
    fun internalPathKeepsGivenTraceId() {
        // when
        val result = client().get().uri("/internal/auth/users/1/authority")
            .header(Headers.TRACE_ID, "trace-12345678")
            .exchange()
            .expectStatus().isNotFound
            .expectBody().returnResult()

        // then
        val body = body(result.responseBody)
        assertEquals("NOT_FOUND", body["code"])
        assertEquals("trace-12345678", body["traceId"])
        assertEquals("trace-12345678", result.responseHeaders.getFirst(Headers.TRACE_ID))
    }

    @Test
    @DisplayName("형식이 맞지 않는 추적 번호는 쓰지 않고 새로 만든다")
    fun unsafeTraceIdIsReplaced() {
        // when
        val result = client().get().uri("/internal/x")
            .header(Headers.TRACE_ID, "bad id<script>")
            .exchange()
            .expectStatus().isNotFound
            .expectBody().returnResult()

        // then
        val traceId = body(result.responseBody)["traceId"] as String
        assertNotEquals("bad id<script>", traceId)
        assertEquals(32, traceId.length)
    }
}
