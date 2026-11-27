package com.sleekydz86.aibackoffice.framework.security.config

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.JsonAccessDeniedHandler
import com.sleekydz86.aibackoffice.framework.security.core.JsonAuthenticationEntryPoint
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.LoginUserJwtConverter
import com.sleekydz86.aibackoffice.framework.security.core.RevocationTokenValidator
import com.sleekydz86.aibackoffice.framework.security.core.SecurityExceptionHandler
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkService
import com.sleekydz86.aibackoffice.framework.security.core.TokenRevocationChecker
import com.sleekydz86.aibackoffice.framework.security.core.rpc.AuthorizationForwardingInterceptor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.json.JsonMapper

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
@EnableConfigurationProperties(AiboSecurityProperties::class)
class AiboSecurityAutoConfiguration {

    @Bean("ss")
    fun securityFrameworkService(): SecurityFrameworkService = SecurityFrameworkService()

    @Bean
    fun securityExceptionHandler(): SecurityExceptionHandler = SecurityExceptionHandler()

    @Bean
    @ConditionalOnMissingBean
    fun jwtDecoder(properties: AiboSecurityProperties, revocation: ObjectProvider<TokenRevocationChecker>): JwtDecoder {
        if (properties.jwkSetUri.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "공개키 주소 aibo.security.jwk-set-uri 가 비어 있습니다.")
        }
        val decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri).build()
        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>(JwtValidators.createDefaultWithIssuer(properties.issuer))
        revocation.ifAvailable?.let { validators.add(RevocationTokenValidator(it)) }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }

    @Bean
    @ConditionalOnMissingBean
    fun securityFilterChain(
        http: HttpSecurity,
        properties: AiboSecurityProperties,
        customizers: ObjectProvider<AuthorizeRequestsCustomizer>,
        jwtDecoder: JwtDecoder,
        jsonMapper: JsonMapper,
    ): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(JsonAuthenticationEntryPoint(jsonMapper))
                it.accessDeniedHandler(JsonAccessDeniedHandler(jsonMapper))
            }
            .authorizeHttpRequests { registry ->
                registry.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                if (properties.permitAllUrls.isNotEmpty()) {
                    registry.requestMatchers(*properties.permitAllUrls.toTypedArray()).permitAll()
                }
                registry.requestMatchers("/internal/**").hasAuthority(LoginUser.SERVICE_AUTHORITY)
                customizers.orderedStream().forEach { it.customize(registry) }
                registry.anyRequest().authenticated()
            }
            .oauth2ResourceServer { server ->
                server.authenticationEntryPoint(JsonAuthenticationEntryPoint(jsonMapper))
                server.jwt { it.decoder(jwtDecoder).jwtAuthenticationConverter(LoginUserJwtConverter()) }
            }
        return http.build()
    }

    @Bean
    fun authorizationForwardingCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder -> builder.requestInterceptor(AuthorizationForwardingInterceptor()) }
}
