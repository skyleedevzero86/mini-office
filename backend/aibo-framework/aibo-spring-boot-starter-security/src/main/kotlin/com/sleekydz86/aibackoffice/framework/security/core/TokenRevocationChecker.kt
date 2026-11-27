package com.sleekydz86.aibackoffice.framework.security.core

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

interface TokenRevocationChecker {

    fun isRevoked(tokenId: String): Boolean

    fun currentPermissionVersion(userId: String): Long?
}

class RevocationTokenValidator(private val checker: TokenRevocationChecker) : OAuth2TokenValidator<Jwt> {

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val user = LoginUserJwtConverter.toLoginUser(token)
        if (user.tokenId != null && checker.isRevoked(user.tokenId)) {
            return failure("로그아웃했거나 폐기된 토큰입니다.")
        }
        if (!user.service) {
            val current = checker.currentPermissionVersion(user.userId)
            if (current != null && current > user.permissionVersion) {
                return failure("권한이 바뀌었습니다. 다시 로그인하세요.")
            }
        }
        return OAuth2TokenValidatorResult.success()
    }

    private fun failure(message: String): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", message, null))
}
