package com.sleekydz86.aibackoffice.auth.api

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/internal/auth")
interface AuthInternalApi {

    @PostExchange("/service-tokens")
    fun issueServiceToken(
        @RequestHeader(CLIENT_ID_HEADER) clientId: String,
        @RequestHeader(CLIENT_SECRET_HEADER) clientSecret: String,
        @RequestBody request: ServiceTokenRequest,
    ): ServiceTokenResponse

    @GetExchange("/users/{userId}/authority")
    fun currentAuthority(@PathVariable userId: String): UserAuthorityResponse

    companion object {
        const val CLIENT_ID_HEADER = "X-Client-Id"
        const val CLIENT_SECRET_HEADER = "X-Client-Secret"
    }
}

data class ServiceTokenRequest(
    val onBehalfOf: String?,
    val purpose: String,
    val approvalId: Long? = null,
)

data class ServiceTokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    val onBehalfOf: String?,
    val perms: List<String>,
    val scopes: List<String>,
)

data class UserAuthorityResponse(
    val userId: String,
    val active: Boolean,
    val roles: List<String>,
    val perms: List<String>,
    val scopes: List<String>,
)
