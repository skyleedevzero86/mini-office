package com.sleekydz86.aibackoffice.auth.presentation.internal

import com.sleekydz86.aibackoffice.auth.api.AuthInternalApi
import com.sleekydz86.aibackoffice.auth.api.ServiceTokenRequest
import com.sleekydz86.aibackoffice.auth.api.ServiceTokenResponse
import com.sleekydz86.aibackoffice.auth.api.UserAuthorityResponse
import com.sleekydz86.aibackoffice.auth.application.ServiceTokenService
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthInternalController(private val serviceTokenService: ServiceTokenService) : AuthInternalApi {

    override fun issueServiceToken(clientId: String, clientSecret: String, request: ServiceTokenRequest): ServiceTokenResponse =
        serviceTokenService.issue(clientId, clientSecret, request)

    override fun currentAuthority(userId: String): UserAuthorityResponse = serviceTokenService.currentAuthority(userId)
}
