package com.sleekydz86.aibackoffice.auth.presentation

import com.sleekydz86.aibackoffice.auth.application.AuthService
import com.sleekydz86.aibackoffice.auth.application.RoleView
import com.sleekydz86.aibackoffice.auth.application.TokenResponse
import com.sleekydz86.aibackoffice.auth.application.UserAdminService
import com.sleekydz86.aibackoffice.auth.application.UserView
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val email: String?, val password: String?)

data class RefreshRequest(val refreshToken: String?)

data class RolesRequest(val roles: List<String> = emptyList())

data class ChannelsRequest(val channels: List<String> = emptyList())

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val userAdminService: UserAdminService,
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): TokenResponse = authService.login(request.email, request.password)

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): TokenResponse = authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    fun logout(@RequestBody(required = false) request: RefreshRequest?): ResponseEntity<Void> {
        authService.logout(SecurityFrameworkUtils.loginUser(), request?.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(): UserView = authService.me(SecurityFrameworkUtils.loginUser())

    @GetMapping("/roles")
    fun roles(): List<RoleView> = userAdminService.roles()

    @GetMapping("/users")
    @PreAuthorize("@ss.hasPermission('policy:write')")
    fun users(): List<UserView> = userAdminService.users()

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("@ss.hasPermission('policy:write')")
    fun replaceRoles(@PathVariable userId: Long, @RequestBody request: RolesRequest): UserView =
        userAdminService.replaceRoles(userId, request.roles)

    @PutMapping("/users/{userId}/scopes")
    @PreAuthorize("@ss.hasPermission('policy:write')")
    fun replaceChannels(@PathVariable userId: Long, @RequestBody request: ChannelsRequest): UserView =
        userAdminService.replaceChannels(userId, request.channels)
}
