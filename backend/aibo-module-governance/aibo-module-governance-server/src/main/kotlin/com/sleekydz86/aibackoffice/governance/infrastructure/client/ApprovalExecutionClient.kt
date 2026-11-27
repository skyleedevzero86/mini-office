package com.sleekydz86.aibackoffice.governance.infrastructure.client

import com.sleekydz86.aibackoffice.auth.api.AuthInternalApi
import com.sleekydz86.aibackoffice.auth.api.ServiceTokenRequest
import com.sleekydz86.aibackoffice.auth.api.ServiceTokenResponse
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.governance.api.ApprovalExecuteRequest
import com.sleekydz86.aibackoffice.governance.infrastructure.config.GovernanceProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

interface DelegatedTokenIssuer {
    fun issueFor(requesterId: String, approvalId: Long): ServiceTokenResponse
}

interface ApprovalExecutor {
    fun execute(token: String, request: ApprovalExecuteRequest): Map<String, Any?>
}

@Component
class AuthDelegatedTokenIssuer(
    private val auth: AuthInternalApi,
    private val properties: GovernanceProperties,
) : DelegatedTokenIssuer {

    override fun issueFor(requesterId: String, approvalId: Long): ServiceTokenResponse {
        if (properties.authUrl.isBlank() || properties.clientSecret.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "auth 주소나 서비스 비밀값 설정이 비어 있습니다.")
        }
        return auth.issueServiceToken(
            properties.clientId,
            properties.clientSecret,
            ServiceTokenRequest(onBehalfOf = requesterId, purpose = "approval-execute", approvalId = approvalId),
        )
    }
}

@Component
class McpApprovalExecutor(
    private val downstreamRestClient: RestClient,
    private val properties: GovernanceProperties,
) : ApprovalExecutor {

    override fun execute(token: String, request: ApprovalExecuteRequest): Map<String, Any?> {
        if (properties.mcpUrl.isBlank()) {
            throw BusinessException(ErrorCode.SERVICE_NOT_CONFIGURED, "mcp-server 주소 설정이 비어 있습니다.")
        }
        @Suppress("UNCHECKED_CAST")
        return downstreamRestClient.post()
            .uri(properties.mcpUrl.trimEnd('/') + "/internal/approvals/${request.approvalId}/execute")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(Map::class.java) as Map<String, Any?>?
            ?: throw BusinessException(ErrorCode.UPSTREAM_FAILED, "mcp-server 가 빈 실행 결과를 돌려주었습니다.")
    }
}
