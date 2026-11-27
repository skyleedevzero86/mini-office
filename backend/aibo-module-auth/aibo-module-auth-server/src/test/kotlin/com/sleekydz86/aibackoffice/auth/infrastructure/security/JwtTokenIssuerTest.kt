package com.sleekydz86.aibackoffice.auth.infrastructure.security

import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUserJwtConverter
import com.sleekydz86.aibackoffice.framework.security.core.RevocationTokenValidator
import com.sleekydz86.aibackoffice.framework.security.core.TokenRevocationChecker
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

class JwtTokenIssuerTest {

    private val properties = AuthProperties(allowEphemeralKey = true)
    private val keys = RsaKeyHolder(properties)
    private val issuer = JwtTokenIssuer(keys, properties)

    private val subject = TokenSubject(
        subject = "7",
        name = "편성 담당",
        team = "편성팀",
        roles = listOf("SCHEDULER"),
        perms = listOf("schedule:read", "schedule:draft", "schedule:publish"),
        scopes = listOf("TVN", "OCN"),
        permissionVersion = 3,
    )

    @Test
    @DisplayName("RS256으로 발급한 토큰을 공개키로 검증하면 권한과 담당 채널이 그대로 나온다")
    fun issuedTokenVerifiesWithPublicKey() {
        // given
        val token = issuer.issue(subject, Duration.ofMinutes(5))
        val decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey).build()

        // when
        val user = LoginUserJwtConverter.toLoginUser(decoder.decode(token.value))

        // then
        assertEquals("7", user.userId)
        assertEquals(TokenType.USER, user.tokenType)
        assertEquals(token.tokenId, user.tokenId)
        assertEquals(3, user.permissionVersion)
        assertTrue(user.has("schedule:publish"))
        assertTrue(user.inChannel("TVN"))
        assertFalse(user.inChannel("JTBC"))
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 거절한다")
    fun tokenSignedByAnotherKeyIsRejected() {
        // given
        val otherKeys = RsaKeyHolder(properties)
        val forged = JwtTokenIssuer(otherKeys, properties).issue(subject, Duration.ofMinutes(5))
        val decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey).build()

        // then
        assertThrows(JwtException::class.java) { decoder.decode(forged.value) }
    }

    @Test
    @DisplayName("폐기한 토큰과 권한이 바뀌기 전에 받은 토큰은 검증에서 떨어진다")
    fun revokedOrStaleTokenFailsValidation() {
        // given
        val token = issuer.issue(subject, Duration.ofMinutes(5))
        val jwt = NimbusJwtDecoder.withPublicKey(keys.publicKey).build().decode(token.value)
        val revoked = RevocationTokenValidator(FakeChecker(revokedTokenId = token.tokenId, version = 3))
        val stale = RevocationTokenValidator(FakeChecker(revokedTokenId = null, version = 4))
        val fresh = RevocationTokenValidator(FakeChecker(revokedTokenId = null, version = 3))

        // then
        assertTrue(revoked.validate(jwt).hasErrors())
        assertTrue(stale.validate(jwt).hasErrors())
        assertFalse(fresh.validate(jwt).hasErrors())
    }

    @Test
    @DisplayName("서비스 토큰은 서비스로 구분되고 대신 실행할 사용자를 담는다")
    fun serviceTokenCarriesOnBehalfOf() {
        // given
        val service = subject.copy(subject = "svc:governance", service = true, onBehalfOf = "7")
        val decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer))
        }

        // when
        val user = LoginUserJwtConverter.toLoginUser(decoder.decode(issuer.issue(service, Duration.ofMinutes(1)).value))

        // then
        assertEquals(TokenType.SERVICE, user.tokenType)
        assertEquals("7", user.effectiveUserId)
    }

    @Test
    @DisplayName("서명 키가 없고 임시 키도 허용하지 않으면 서버가 뜨지 않는다")
    fun missingKeyFailsFast() {
        // when
        val exception = assertThrows(BusinessException::class.java) { RsaKeyHolder(AuthProperties()) }

        // then
        assertEquals(ErrorCode.SERVICE_NOT_CONFIGURED, exception.code)
    }

    private class FakeChecker(private val revokedTokenId: String?, private val version: Long?) : TokenRevocationChecker {
        override fun isRevoked(tokenId: String): Boolean = tokenId == revokedTokenId
        override fun currentPermissionVersion(userId: String): Long? = version
    }
}
