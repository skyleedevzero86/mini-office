package com.sleekydz86.aibackoffice.framework.security.config

import org.springframework.core.Ordered
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

fun interface AuthorizeRequestsCustomizer : Ordered {

    fun customize(registry: AuthorizeHttpRequestsConfigurer<*>.AuthorizationManagerRequestMatcherRegistry)

    override fun getOrder(): Int = 0
}
