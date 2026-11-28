package com.sleekydz86.aibackoffice.mcp.infrastructure.security

import com.sleekydz86.aibackoffice.framework.security.config.AuthorizeRequestsCustomizer
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.web.access.intercept.RequestAuthorizationContext

@Configuration(proxyBeanMethods = false)
class McpSecurityConfiguration {

    @Bean
    fun mcpAuthorizeRequests(properties: McpProperties): AuthorizeRequestsCustomizer = AuthorizeRequestsCustomizer { registry ->
        registry.requestMatchers(properties.endpoint, "${properties.endpoint}/**", "/api/mcp/**").access(USER_TOKEN_ONLY)
    }

    companion object {
        val USER_TOKEN_ONLY: AuthorizationManager<RequestAuthorizationContext> = AuthorizationManager { authentication, _ ->
            val current = authentication.get()
            val user = current?.principal as? LoginUser
            AuthorizationDecision(current != null && current.isAuthenticated && user != null && !user.service)
        }
    }
}
