package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.auth.api.ServiceTokenResponse
import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.common.RiskLevel
import com.sleekydz86.aibackoffice.common.Zones
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.TokenType
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.governance.api.ApprovalCreateRequest
import com.sleekydz86.aibackoffice.governance.api.ApprovalExecuteRequest
import com.sleekydz86.aibackoffice.governance.domain.ApprovalRequest
import com.sleekydz86.aibackoffice.governance.domain.ApprovalStatus
import com.sleekydz86.aibackoffice.governance.domain.ToolPolicy
import com.sleekydz86.aibackoffice.governance.infrastructure.client.ApprovalExecutor
import com.sleekydz86.aibackoffice.governance.infrastructure.client.DelegatedTokenIssuer
import com.sleekydz86.aibackoffice.governance.infrastructure.config.GovernanceProperties
import com.sleekydz86.aibackoffice.governance.infrastructure.persistence.ApprovalStore
import com.sleekydz86.aibackoffice.governance.infrastructure.persistence.ToolPolicyStore
import java.time.Clock
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class ApprovalServiceTest {

    private val now = LocalDateTime.of(2026, 12, 4, 10, 0)
    private val clock = Clock.fixed(now.atZone(Zones.SEOUL).toInstant(), Zones.SEOUL)

    private val approvals = mock<ApprovalStore>()
    private val policies = mock<ToolPolicyStore>()
    private val tokens = mock<DelegatedTokenIssuer>()
    private val executor = mock<ApprovalExecutor>()
    private val audit = mock<AuditRecorder>()
    private val views = GovernanceViews(JsonMapper.builder().addModule(KotlinModule.Builder().build()).build())
    private val transactions = TransactionTemplate(
        mock<PlatformTransactionManager> { on { getTransaction(anyOrNull()) } doReturn SimpleTransactionStatus() },
    )
    private val service = ApprovalService(approvals, policies, views, tokens, executor, audit, transactions, GovernanceProperties(), clock)

    private val publishPolicy = ToolPolicy("schedule_publish_slot", "schedule", RiskLevel.WRITE_HIGH, Permissions.SCHEDULE_PUBLISH, true, 1440, now)

    private fun user(
        id: String,
        perms: Set<String>,
        scopes: Set<String> = setOf("TVN"),
        service: Boolean = false,
        onBehalfOf: String? = null,
    ) = LoginUser(id, "사용자$id", null, emptySet(), perms, scopes, 0, null, if (service) TokenType.SERVICE else TokenType.USER, onBehalfOf, ActorType.HUMAN, "token")

    private val requester = user("7", setOf(Permissions.SCHEDULE_PUBLISH))
    private val approver = user("4", setOf(Permissions.APPROVAL_DECIDE))

    private fun pending(status: ApprovalStatus = ApprovalStatus.PENDING, requesterId: String = "7", expiresAt: LocalDateTime = now.plusHours(1)) =
        ApprovalRequest(
            id = 55, toolName = "schedule_publish_slot", argumentsJson = "{\"slotId\":10}", riskLevel = RiskLevel.WRITE_HIGH,
            requesterId = requesterId, requesterName = "요청자", requesterType = ActorType.AGENT, conversationId = null,
            scopeChannel = "TVN", reason = "게시", status = status, approverId = null, decisionComment = null, decidedAt = null,
            expiresAt = expiresAt, idempotencyKey = "k", resultJson = null, traceId = null, createdAt = now.minusMinutes(5),
        )

    private fun token(perms: List<String> = listOf(Permissions.SCHEDULE_PUBLISH), scopes: List<String> = listOf("TVN")) =
        ServiceTokenResponse("svc-token", 300, "7", perms, scopes)

    @Test
    @DisplayName("요청한 사람은 자기 요청을 승인할 수 없다")
    fun selfApprovalIsRejected() {
        // given
        whenever(approvals.find(55)).thenReturn(pending(requesterId = "4"))

        // when
        val error = assertThrows<BusinessException> { service.approve(approver, 55, ApprovalDecisionCommand()) }

        // then
        assertEquals(ErrorCode.SELF_APPROVAL, error.code)
        verify(approvals, never()).decide(any(), any(), any(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    @DisplayName("요청 대상 채널을 맡지 않은 사람은 승인할 수 없다")
    fun approverMustOwnChannel() {
        // given
        whenever(approvals.find(55)).thenReturn(pending())

        // when
        val error = assertThrows<BusinessException> {
            service.approve(user("4", setOf(Permissions.APPROVAL_DECIDE), scopes = setOf("OCN")), 55, ApprovalDecisionCommand())
        }

        // then
        assertEquals(ErrorCode.SCOPE_DENIED, error.code)
    }

    @Test
    @DisplayName("다른 사람이 먼저 결정했으면 두 번째 승인은 실행하지 않는다")
    fun secondApprovalDoesNotExecute() {
        // given
        whenever(approvals.find(55)).thenReturn(pending())
        whenever(approvals.decide(eq(55), eq(ApprovalStatus.PENDING), eq(ApprovalStatus.APPROVED), any(), anyOrNull(), any())).thenReturn(false)

        // when
        val error = assertThrows<BusinessException> { service.approve(approver, 55, ApprovalDecisionCommand()) }

        // then
        assertEquals(ErrorCode.INVALID_STATE, error.code)
        verify(tokens, never()).issueFor(any(), any())
        verify(executor, never()).execute(any(), any())
    }

    @Test
    @DisplayName("기다리는 시간이 지난 요청은 승인할 수 없다")
    fun expiredRequestCannotBeApproved() {
        // given
        whenever(approvals.find(55)).thenReturn(pending(expiresAt = now.minusMinutes(1)))

        // when
        val error = assertThrows<BusinessException> { service.approve(approver, 55, ApprovalDecisionCommand()) }

        // then
        assertEquals(ErrorCode.INVALID_STATE, error.code)
    }

    @Test
    @DisplayName("승인하면 요청자의 지금 권한으로 토큰을 받아 실행하고 실행 완료로 남긴다")
    fun approveExecutesWithRequesterAuthority() {
        // given
        whenever(approvals.find(55)).thenReturn(pending(), pending(ApprovalStatus.EXECUTED))
        whenever(approvals.decide(eq(55), eq(ApprovalStatus.PENDING), eq(ApprovalStatus.APPROVED), eq("4"), anyOrNull(), any())).thenReturn(true)
        whenever(policies.find("schedule_publish_slot")).thenReturn(publishPolicy)
        whenever(tokens.issueFor("7", 55)).thenReturn(token())
        whenever(executor.execute(eq("svc-token"), any())).thenReturn(mapOf("ok" to true, "data" to mapOf("status" to "PUBLISHED")))
        whenever(approvals.finish(eq(55), eq(ApprovalStatus.EXECUTED), any())).thenReturn(true)

        // when
        val view = service.approve(approver, 55, ApprovalDecisionCommand("확인"))

        // then
        assertEquals("EXECUTED", view.status)
        val request = argumentCaptor<ApprovalExecuteRequest>()
        verify(executor).execute(eq("svc-token"), request.capture())
        assertEquals("approval:55", request.firstValue.idempotencyKey)
        assertEquals(10, request.firstValue.arguments["slotId"])
    }

    @Test
    @DisplayName("승인을 기다리는 사이 요청자 권한이 빠졌으면 실행하지 않고 실행 실패로 남긴다")
    fun revokedPermissionFailsExecution() {
        // given
        whenever(approvals.find(55)).thenReturn(pending(), pending(ApprovalStatus.FAILED))
        whenever(approvals.decide(eq(55), eq(ApprovalStatus.PENDING), eq(ApprovalStatus.APPROVED), any(), anyOrNull(), any())).thenReturn(true)
        whenever(policies.find("schedule_publish_slot")).thenReturn(publishPolicy)
        whenever(tokens.issueFor("7", 55)).thenReturn(token(perms = listOf(Permissions.SCHEDULE_READ)))
        whenever(approvals.finish(eq(55), eq(ApprovalStatus.FAILED), any())).thenReturn(true)

        // when
        service.approve(approver, 55, ApprovalDecisionCommand())

        // then
        val result = argumentCaptor<String>()
        verify(approvals).finish(eq(55), eq(ApprovalStatus.FAILED), result.capture())
        assertEquals("PERMISSION_DENIED", views.readMap(result.firstValue)?.get("code"))
        verify(executor, never()).execute(any(), any())
    }

    @Test
    @DisplayName("실행을 맡은 서비스가 실패로 답하면 실행 실패로 남긴다")
    fun downstreamFailureIsRecorded() {
        // given
        whenever(approvals.find(55)).thenReturn(pending(), pending(ApprovalStatus.FAILED))
        whenever(approvals.decide(eq(55), eq(ApprovalStatus.PENDING), eq(ApprovalStatus.APPROVED), any(), anyOrNull(), any())).thenReturn(true)
        whenever(policies.find("schedule_publish_slot")).thenReturn(publishPolicy)
        whenever(tokens.issueFor("7", 55)).thenReturn(token())
        whenever(executor.execute(any(), any())).thenReturn(mapOf("ok" to false, "code" to "CONFLICT", "message" to "시간이 겹칩니다"))
        whenever(approvals.finish(eq(55), eq(ApprovalStatus.FAILED), any())).thenReturn(true)

        // when
        service.approve(approver, 55, ApprovalDecisionCommand())

        // then
        verify(approvals).finish(eq(55), eq(ApprovalStatus.FAILED), any())
    }

    @Test
    @DisplayName("반려에는 의견이 필요하다")
    fun rejectNeedsComment() {
        // when
        val error = assertThrows<BusinessException> { service.reject(approver, 55, ApprovalDecisionCommand("  ")) }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
    }

    @Test
    @DisplayName("승인이 필요 없는 툴은 승인 요청을 올릴 수 없다")
    fun cannotSubmitLowRiskTool() {
        // given
        whenever(policies.find("schedule_create_draft"))
            .thenReturn(ToolPolicy("schedule_create_draft", "schedule", RiskLevel.WRITE_LOW, Permissions.SCHEDULE_DRAFT, true, 0, now))

        // when
        val error = assertThrows<BusinessException> {
            service.submitByHuman(requester, ApprovalSubmitCommand("schedule_create_draft", emptyMap(), "초안"), null)
        }

        // then
        assertEquals(ErrorCode.VALIDATION_FAILED, error.code)
    }

    @Test
    @DisplayName("툴 권한이 없는 사람은 승인 요청도 올릴 수 없다")
    fun submitNeedsToolPermission() {
        // given
        whenever(policies.find("schedule_publish_slot")).thenReturn(publishPolicy)

        // when
        val error = assertThrows<BusinessException> {
            service.submitByHuman(user("8", setOf(Permissions.SCHEDULE_READ)), ApprovalSubmitCommand("schedule_publish_slot", mapOf("slotId" to 10), "게시"), null)
        }

        // then
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code)
    }

    @Test
    @DisplayName("서비스가 대신 올린 요청은 원래 사용자를 요청자로 남기고 대기 상태로 만든다")
    fun serviceSubmitRecordsRequester() {
        // given
        whenever(policies.find("schedule_publish_slot")).thenReturn(publishPolicy)
        whenever(approvals.findByIdempotencyKey(any())).thenReturn(null)
        whenever(approvals.insert(any())).thenReturn(55)
        whenever(approvals.find(55)).thenReturn(pending())
        val mcp = user("svc:mcp", setOf(Permissions.SCHEDULE_PUBLISH), service = true, onBehalfOf = "7")

        // when
        service.submitByService(mcp, ApprovalCreateRequest("schedule_publish_slot", mapOf("slotId" to 10), "게시", "AGENT", scopeChannel = "tvn", idempotencyKey = "call-1"))

        // then
        val saved = argumentCaptor<ApprovalRequest>()
        verify(approvals).insert(saved.capture())
        assertEquals("7", saved.firstValue.requesterId)
        assertEquals(ActorType.AGENT, saved.firstValue.requesterType)
        assertEquals("TVN", saved.firstValue.scopeChannel)
        assertEquals("7:call-1", saved.firstValue.idempotencyKey)
        assertEquals(now.plusMinutes(1440), saved.firstValue.expiresAt)
    }

    @Test
    @DisplayName("승인 권한이 없으면 목록에서 내 요청만 본다")
    fun listShowsOnlyMineWithoutDecide() {
        // given
        whenever(approvals.list(anyOrNull(), anyOrNull(), any(), any(), any())).thenReturn(emptyList())

        // when
        service.list(requester, ApprovalFilter(), ListQuery())

        // then
        verify(approvals).list(null, "7", 0, 20, false)
    }

    @Test
    @DisplayName("기다리는 시간이 지난 요청은 만료로 바꾼다")
    fun expiresDueRequests() {
        // given
        whenever(approvals.findExpired(eq(now), any())).thenReturn(listOf(55L, 56L))
        whenever(approvals.decide(eq(55), eq(ApprovalStatus.PENDING), eq(ApprovalStatus.EXPIRED), anyOrNull(), anyOrNull(), any())).thenReturn(true)
        whenever(approvals.decide(eq(56), eq(ApprovalStatus.PENDING), eq(ApprovalStatus.EXPIRED), anyOrNull(), anyOrNull(), any())).thenReturn(false)

        // when
        val expired = service.expireDue()

        // then
        assertEquals(1, expired)
    }
}
