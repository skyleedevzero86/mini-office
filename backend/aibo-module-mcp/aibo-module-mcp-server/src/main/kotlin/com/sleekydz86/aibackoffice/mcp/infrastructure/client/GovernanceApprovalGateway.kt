package com.sleekydz86.aibackoffice.mcp.infrastructure.client

import com.sleekydz86.aibackoffice.auth.api.AuthInternalApi
import com.sleekydz86.aibackoffice.auth.api.ServiceTokenRequest
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.governance.api.ApprovalCreateRequest
import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
import com.sleekydz86.aibackoffice.governance.api.GovernanceInternalApi
import com.sleekydz86.aibackoffice.mcp.application.ApprovalDraft
import com.sleekydz86.aibackoffice.mcp.application.ApprovalGateway
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpConfiguration
import com.sleekydz86.aibackoffice.mcp.infrastructure.config.McpProperties
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GovernanceApprovalGateway(
    private val auth: AuthInternalApi,
    private val downstreamRestClient: RestClient,
    private val properties: McpProperties,
) : ApprovalGateway {

    override fun request(user: LoginUser, draft: ApprovalDraft): ApprovalResponse {
        if (properties.authUrl.isBlank() || properties.governanceUrl.isBlank() || properties.clientSecret.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "auth 주소, governance 주소, 서비스 비밀값 설정 중 비어 있는 것이 있습니다.")
        }
        val token = auth.issueServiceToken(
            properties.clientId,
            properties.clientSecret,
            ServiceTokenRequest(onBehalfOf = user.effectiveUserId, purpose = "approval-request"),
        )
        val client = downstreamRestClient.mutate()
            .baseUrl(properties.governanceUrl.trimEnd('/'))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${token.accessToken}")
            .build()
        return McpConfiguration.proxy(client, GovernanceInternalApi::class.java).requestApproval(
            ApprovalCreateRequest(
                toolName = draft.toolName,
                arguments = draft.arguments,
                reason = draft.reason,
                requesterType = user.actorType.name,
                requesterName = user.name,
                conversationId = draft.conversationId,
                scopeChannel = draft.scopeChannel,
                idempotencyKey = draft.idempotencyKey,
            ),
        )
    }
}
