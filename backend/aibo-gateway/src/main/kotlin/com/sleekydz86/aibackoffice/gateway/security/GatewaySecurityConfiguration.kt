package com.sleekydz86.aibackoffice.gateway.security

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.gateway.config.GatewayProperties
import com.sleekydz86.aibackoffice.gateway.error.GatewayErrors
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class GatewaySecurityConfiguration {

    @Bean
    fun reactiveJwtDecoder(properties: GatewayProperties): ReactiveJwtDecoder {
        if (properties.jwkSetUri.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "공개키 주소 aibo.gateway.jwk-set-uri 가 비어 있습니다.")
        }
        return NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer))
        }
    }

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        properties: GatewayProperties,
        decoder: ReactiveJwtDecoder,
        jsonMapper: JsonMapper,
    ): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .authorizeExchange { exchanges ->
            exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll()
            exchanges.pathMatchers(*properties.publicPaths.toTypedArray()).permitAll()
            exchanges.pathMatchers(HttpMethod.GET, "/api/content/**").hasAuthority(Permissions.CONTENT_READ)
            exchanges.pathMatchers("/api/content/**").hasAuthority(Permissions.CONTENT_WRITE)
            exchanges.anyExchange().authenticated()
        }
        .exceptionHandling { handling ->
            handling.authenticationEntryPoint { exchange, _ -> writeError(exchange, jsonMapper, ErrorCode.UNAUTHORIZED) }
            handling.accessDeniedHandler { exchange, _ -> writeError(exchange, jsonMapper, ErrorCode.PERMISSION_DENIED) }
        }
        .oauth2ResourceServer { server ->
            server.authenticationEntryPoint { exchange, _ -> writeError(exchange, jsonMapper, ErrorCode.UNAUTHORIZED) }
            server.jwt { it.jwtDecoder(decoder).jwtAuthenticationConverter(PermissionAuthoritiesConverter()) }
        }
        .build()

    private fun writeError(exchange: ServerWebExchange, jsonMapper: JsonMapper, code: ErrorCode): Mono<Void> =
        GatewayErrors.write(exchange, jsonMapper, code)
}

class PermissionAuthoritiesConverter : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        val authorities = buildList<GrantedAuthority> {
            jwt.getClaimAsStringList("perms")?.forEach { add(SimpleGrantedAuthority(it)) }
            jwt.getClaimAsStringList("roles")?.forEach { add(SimpleGrantedAuthority("ROLE_$it")) }
        }
        return Mono.just(JwtAuthenticationToken(jwt, authorities, jwt.subject ?: ""))
    }
}
