package com.sleekydz86.aibackoffice.auth.application

import com.sleekydz86.aibackoffice.auth.api.ServiceTokenRequest
import com.sleekydz86.aibackoffice.auth.api.ServiceTokenResponse
import com.sleekydz86.aibackoffice.auth.api.UserAuthorityResponse
import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.UserRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.security.JwtTokenIssuer
import com.sleekydz86.aibackoffice.auth.infrastructure.security.TokenSubject
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import java.security.MessageDigest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ServiceTokenService(
    private val users: UserRepository,
    private val issuer: JwtTokenIssuer,
    private val authService: AuthService,
    private val properties: AuthProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun issue(clientId: String, clientSecret: String, request: ServiceTokenRequest): ServiceTokenResponse {
        val expected = properties.serviceClients[clientId]
        if (expected.isNullOrBlank() || !MessageDigest.isEqual(expected.toByteArray(), clientSecret.toByteArray())) {
            log.warn("서비스 토큰 요청을 거절했습니다. 서비스={}", clientId)
            throw BusinessException(ErrorCode.UNAUTHORIZED, "서비스 인증에 실패했습니다.")
        }
        val delegated = request.onBehalfOf?.let { userId ->
            val user = userId.toLongOrNull()?.let { users.findById(it).orElse(null) }
            if (user == null || !user.active) {
                throw BusinessException(ErrorCode.PERMISSION_DENIED, "대신 실행할 사용자가 없거나 잠겼습니다.")
            }
            authService.subjectOf(user)
        }
        val subject = TokenSubject(
            subject = "svc:$clientId",
            name = clientId,
            team = null,
            roles = emptyList(),
            perms = delegated?.perms.orEmpty(),
            scopes = delegated?.scopes.orEmpty(),
            permissionVersion = delegated?.permissionVersion ?: 0L,
            service = true,
            onBehalfOf = request.onBehalfOf,
        )
        val token = issuer.issue(subject, properties.serviceTokenTtl)
        log.info("서비스 토큰을 발급했습니다. 서비스={} 대신={} 목적={} 승인={}", clientId, request.onBehalfOf, request.purpose, request.approvalId)
        return ServiceTokenResponse(token.value, token.expiresIn.seconds, request.onBehalfOf, subject.perms.toList(), subject.scopes.toList())
    }

    @Transactional(readOnly = true)
    fun currentAuthority(userId: String): UserAuthorityResponse {
        val user = userId.toLongOrNull()?.let { users.findById(it).orElse(null) }
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        return UserAuthorityResponse(
            userId = userId,
            active = user.active,
            roles = user.roleCodes().toList(),
            perms = user.permissionCodes().toList(),
            scopes = user.channelCodes().toList(),
        )
    }
}
