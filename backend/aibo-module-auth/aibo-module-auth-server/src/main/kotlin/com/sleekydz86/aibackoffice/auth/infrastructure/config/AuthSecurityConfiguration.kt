package com.sleekydz86.aibackoffice.auth.infrastructure.config

import com.sleekydz86.aibackoffice.auth.infrastructure.security.RsaKeyHolder
import com.sleekydz86.aibackoffice.framework.security.core.RevocationTokenValidator
import com.sleekydz86.aibackoffice.framework.security.core.TokenRevocationChecker
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties::class)
class AuthSecurityConfiguration {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtDecoder(keys: RsaKeyHolder, properties: AuthProperties, revocation: ObjectProvider<TokenRevocationChecker>): JwtDecoder {
        val decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey).build()
        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>(JwtValidators.createDefaultWithIssuer(properties.issuer))
        revocation.ifAvailable?.let { validators.add(RevocationTokenValidator(it)) }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }
}
