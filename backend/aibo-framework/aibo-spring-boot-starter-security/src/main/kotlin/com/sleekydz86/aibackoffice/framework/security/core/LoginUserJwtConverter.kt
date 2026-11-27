package com.sleekydz86.aibackoffice.framework.security.core

import com.sleekydz86.aibackoffice.common.ActorType
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

class LoginUserAuthentication(
    val loginUser: LoginUser,
    private val jwt: Jwt,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = jwt

    override fun getPrincipal(): Any = loginUser

    override fun getName(): String = loginUser.userId
}

class LoginUserJwtConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val user = toLoginUser(jwt)
        val authorities = buildList<GrantedAuthority> {
            user.perms.forEach { add(SimpleGrantedAuthority(it)) }
            user.roles.forEach { add(SimpleGrantedAuthority(LoginUser.ROLE_PREFIX + it)) }
            if (user.service) add(SimpleGrantedAuthority(LoginUser.SERVICE_AUTHORITY))
        }
        return LoginUserAuthentication(user, jwt, authorities)
    }

    companion object {
        fun toLoginUser(jwt: Jwt): LoginUser {
            val subject = jwt.subject ?: throw IllegalArgumentException("토큰에 사용자 번호가 없습니다.")
            val type = if (jwt.getClaimAsString(JwtClaimNames.TOKEN_TYPE) == "service") TokenType.SERVICE else TokenType.USER
            return LoginUser(
                userId = subject,
                name = jwt.getClaimAsString(JwtClaimNames.NAME) ?: subject,
                team = jwt.getClaimAsString(JwtClaimNames.TEAM),
                roles = jwt.getClaimAsStringList(JwtClaimNames.ROLES)?.toSet().orEmpty(),
                perms = jwt.getClaimAsStringList(JwtClaimNames.PERMS)?.toSet().orEmpty(),
                scopes = jwt.getClaimAsStringList(JwtClaimNames.SCOPES)?.toSet().orEmpty(),
                permissionVersion = (jwt.getClaim<Any>(JwtClaimNames.PERMISSION_VERSION) as? Number)?.toLong() ?: 0L,
                tokenId = jwt.id,
                tokenType = type,
                onBehalfOf = jwt.getClaimAsString(JwtClaimNames.ON_BEHALF_OF),
                actorType = if (type == TokenType.SERVICE) ActorType.SYSTEM else ActorType.HUMAN,
                rawToken = jwt.tokenValue,
            )
        }
    }
}
