package com.sleekydz86.aibackoffice.auth.infrastructure.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.framework.security.core.JwtClaimNames
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component

data class TokenSubject(
    val subject: String,
    val name: String,
    val team: String?,
    val roles: Collection<String>,
    val perms: Collection<String>,
    val scopes: Collection<String>,
    val permissionVersion: Long,
    val service: Boolean = false,
    val onBehalfOf: String? = null,
)

data class IssuedToken(val value: String, val tokenId: String, val expiresIn: Duration)

@Component
class JwtTokenIssuer(
    private val keys: RsaKeyHolder,
    private val properties: AuthProperties,
) {
    private val encoder = NimbusJwtEncoder(
        ImmutableJWKSet<SecurityContext>(JWKSet(RSAKey.Builder(keys.publicKey).privateKey(keys.privateKey).keyID(keys.keyId).build())),
    )
    private val clock: Clock = Clock.systemUTC()

    fun issue(subject: TokenSubject, ttl: Duration): IssuedToken {
        val now = clock.instant()
        val tokenId = UUID.randomUUID().toString()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(subject.subject)
            .id(tokenId)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim(JwtClaimNames.NAME, subject.name)
            .claim(JwtClaimNames.ROLES, subject.roles.toList())
            .claim(JwtClaimNames.PERMS, subject.perms.toList())
            .claim(JwtClaimNames.SCOPES, subject.scopes.toList())
            .claim(JwtClaimNames.PERMISSION_VERSION, subject.permissionVersion)
            .claim(JwtClaimNames.TOKEN_TYPE, if (subject.service) "service" else "user")
            .apply {
                subject.team?.let { claim(JwtClaimNames.TEAM, it) }
                subject.onBehalfOf?.let { claim(JwtClaimNames.ON_BEHALF_OF, it) }
            }
            .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.keyId).build()
        val value = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedToken(value, tokenId, ttl)
    }
}
