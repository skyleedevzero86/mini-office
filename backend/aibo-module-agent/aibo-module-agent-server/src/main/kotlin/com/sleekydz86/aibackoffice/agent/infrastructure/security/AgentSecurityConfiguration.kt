package com.sleekydz86.aibackoffice.agent.infrastructure.security

import com.sleekydz86.aibackoffice.framework.security.config.AuthorizeRequestsCustomizer
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.web.access.intercept.RequestAuthorizationContext

@Configuration(proxyBeanMethods = false)
class AgentSecurityConfiguration {

    @Bean
    fun agentAuthorizeRequests(): AuthorizeRequestsCustomizer = AuthorizeRequestsCustomizer { registry ->
        registry.requestMatchers("/api/agent/**").access(USER_TOKEN_ONLY)
    }

    companion object {
        val USER_TOKEN_ONLY: AuthorizationManager<RequestAuthorizationContext> = AuthorizationManager { authentication, _ ->
            val current = authentication.get()
            val user = current?.principal as? LoginUser
            AuthorizationDecision(current != null && current.isAuthenticated && user != null && !user.service)
        }
    }
}
