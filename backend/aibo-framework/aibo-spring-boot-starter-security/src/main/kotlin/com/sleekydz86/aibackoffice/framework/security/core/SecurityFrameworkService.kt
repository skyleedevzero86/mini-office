package com.sleekydz86.aibackoffice.framework.security.core

class SecurityFrameworkService {

    fun hasPermission(permission: String): Boolean = SecurityFrameworkUtils.loginUserOrNull()?.has(permission) == true

    fun hasAnyPermissions(vararg permissions: String): Boolean =
        SecurityFrameworkUtils.loginUserOrNull()?.hasAny(*permissions) == true

    fun hasRole(role: String): Boolean = SecurityFrameworkUtils.loginUserOrNull()?.roles?.contains(role) == true

    fun inChannel(channelCode: String?): Boolean = SecurityFrameworkUtils.loginUserOrNull()?.inChannel(channelCode) == true

    fun isService(): Boolean = SecurityFrameworkUtils.loginUserOrNull()?.service == true
}
