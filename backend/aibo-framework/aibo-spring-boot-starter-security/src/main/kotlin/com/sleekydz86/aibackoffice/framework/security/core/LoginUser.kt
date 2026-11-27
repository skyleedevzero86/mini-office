package com.sleekydz86.aibackoffice.framework.security.core

import com.sleekydz86.aibackoffice.common.ActorType

data class LoginUser(
    val userId: String,
    val name: String,
    val team: String?,
    val roles: Set<String>,
    val perms: Set<String>,
    val scopes: Set<String>,
    val permissionVersion: Long,
    val tokenId: String?,
    val tokenType: TokenType,
    val onBehalfOf: String?,
    val actorType: ActorType,
    val rawToken: String,
) {
    val service: Boolean get() = tokenType == TokenType.SERVICE

    val effectiveUserId: String get() = onBehalfOf ?: userId

    fun has(permission: String): Boolean = permission in perms

    fun hasAny(vararg permissions: String): Boolean = permissions.any { it in perms }

    fun inChannel(channelCode: String?): Boolean = channelCode != null && channelCode in scopes

    companion object {
        const val SERVICE_AUTHORITY = "SERVICE"
        const val ROLE_PREFIX = "ROLE_"
    }
}

enum class TokenType { USER, SERVICE }

object JwtClaimNames {
    const val NAME = "name"
    const val TEAM = "team"
    const val ROLES = "roles"
    const val PERMS = "perms"
    const val SCOPES = "scopes"
    const val PERMISSION_VERSION = "pv"
    const val TOKEN_TYPE = "typ"
    const val ON_BEHALF_OF = "on_behalf_of"
}
