package com.sleekydz86.aibackoffice.auth.application

import com.sleekydz86.aibackoffice.auth.domain.RefreshToken
import com.sleekydz86.aibackoffice.auth.domain.User
import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.RefreshTokenRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.UserRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.security.JwtTokenIssuer
import com.sleekydz86.aibackoffice.auth.infrastructure.security.TokenSubject
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.redis.core.RedisTokenRevocationChecker
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.HexFormat
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val issuer: JwtTokenIssuer,
    private val revocation: RedisTokenRevocationChecker,
    private val loginGuard: LoginAttemptGuard,
    private val properties: AuthProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val timingHash: String by lazy { passwordEncoder.encode("timing-equalizer") ?: "" }

    @Transactional
    fun login(email: String?, password: String?): TokenResponse {
        val normalized = email?.trim()?.lowercase()
        if (normalized.isNullOrBlank() || password.isNullOrEmpty()) throw BusinessException(ErrorCode.BAD_CREDENTIALS)
        loginGuard.requireNotLocked(normalized)
        val user = users.findByEmail(normalized)
        if (user == null) {
            passwordEncoder.matches(password, timingHash)
            loginGuard.recordFailure(normalized)
            throw BusinessException(ErrorCode.BAD_CREDENTIALS)
        }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            loginGuard.recordFailure(normalized)
            throw BusinessException(ErrorCode.BAD_CREDENTIALS)
        }
        if (!user.active) throw BusinessException(ErrorCode.ACCOUNT_LOCKED, "관리자가 잠근 계정입니다.")
        loginGuard.clear(normalized)
        log.info("로그인했습니다. 사용자={}", user.id)
        return issueTokens(user)
    }

    @Transactional
    fun refresh(refreshToken: String?): TokenResponse {
        if (refreshToken.isNullOrBlank()) throw BusinessException(ErrorCode.UNAUTHORIZED)
        val now = LocalDateTime.now()
        val stored = refreshTokens.findByTokenHash(sha256(refreshToken))
        if (stored == null || !stored.usable(now)) throw BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인하세요.")
        stored.revoke(now)
        val user = users.findById(stored.userId).orElse(null)
        if (user == null || !user.active) throw BusinessException(ErrorCode.UNAUTHORIZED, "다시 로그인하세요.")
        return issueTokens(user)
    }

    @Transactional
    fun logout(loginUser: LoginUser, refreshToken: String?) {
        val now = LocalDateTime.now()
        refreshToken?.takeIf { it.isNotBlank() }?.let { token ->
            refreshTokens.findByTokenHash(sha256(token))
                ?.takeIf { it.userId.toString() == loginUser.userId }
                ?.revoke(now)
        }
        loginUser.tokenId?.let { revocation.revoke(it, properties.accessTokenTtl) }
    }

    @Transactional(readOnly = true)
    fun me(loginUser: LoginUser): UserView = UserView.from(requireUser(loginUser.userId))

    fun subjectOf(user: User): TokenSubject = TokenSubject(
        subject = user.requireId().toString(),
        name = user.name,
        team = user.team?.name,
        roles = user.roleCodes(),
        perms = user.permissionCodes(),
        scopes = user.channelCodes(),
        permissionVersion = revocation.currentPermissionVersion(user.requireId().toString()) ?: 0L,
    )

    private fun issueTokens(user: User): TokenResponse {
        val access = issuer.issue(subjectOf(user), properties.accessTokenTtl)
        val refresh = newRefreshToken()
        refreshTokens.save(RefreshToken(user.requireId(), sha256(refresh), LocalDateTime.now().plus(properties.refreshTokenTtl)))
        return TokenResponse(access.value, refresh, access.expiresIn.seconds, UserView.from(user))
    }

    private fun requireUser(userId: String): User =
        userId.toLongOrNull()?.let { users.findById(it).orElse(null) }
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")

    private fun newRefreshToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun sha256(value: String): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
    }
}
