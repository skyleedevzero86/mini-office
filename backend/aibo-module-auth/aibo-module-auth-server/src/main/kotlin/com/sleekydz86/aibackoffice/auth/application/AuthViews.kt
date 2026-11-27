package com.sleekydz86.aibackoffice.auth.application

import com.sleekydz86.aibackoffice.auth.domain.User

data class UserView(
    val id: Long,
    val email: String,
    val name: String,
    val team: String?,
    val status: String,
    val roles: List<String>,
    val perms: List<String>,
    val scopes: List<String>,
) {
    companion object {
        fun from(user: User) = UserView(
            id = user.requireId(),
            email = user.email,
            name = user.name,
            team = user.team?.name,
            status = user.status.name,
            roles = user.roleCodes().toList(),
            perms = user.permissionCodes().toList(),
            scopes = user.channelCodes().toList(),
        )
    }
}

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserView,
)

data class RoleView(val code: String, val name: String, val permissions: List<String>)
