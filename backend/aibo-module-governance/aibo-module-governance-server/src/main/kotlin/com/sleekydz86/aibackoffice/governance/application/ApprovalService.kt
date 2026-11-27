package com.sleekydz86.aibackoffice.governance.application

import com.sleekydz86.aibackoffice.common.ActorType
import com.sleekydz86.aibackoffice.common.AuditEvent
import com.sleekydz86.aibackoffice.common.AuditResult
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.Permissions
import com.sleekydz86.aibackoffice.framework.audit.core.AuditRecorder
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.framework.security.core.SecurityFrameworkUtils
import com.sleekydz86.aibackoffice.framework.web.core.list.ListQuery
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import com.sleekydz86.aibackoffice.governance.api.ApprovalCreateRequest
import com.sleekydz86.aibackoffice.governance.api.ApprovalExecuteRequest
import com.sleekydz86.aibackoffice.governance.api.ApprovalResponse
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
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

data class SubmitSource(
    val requesterId: String,
    val requesterName: String?,
    val requesterType: ActorType,
    val conversationId: String?,
    val scopeChannel: String?,
    val idempotencyKey: String?,
)

@Service
class ApprovalService(
    private val approvals: ApprovalStore,
    private val policies: ToolPolicyStore,
    private val views: GovernanceViews,
    private val tokens: DelegatedTokenIssuer,
    private val executor: ApprovalExecutor,
    private val audit: AuditRecorder,
    private val transactions: TransactionTemplate,
    private val properties: GovernanceProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun list(user: LoginUser, filter: ApprovalFilter, query: ListQuery): ApprovalPage {
        val requesterId = if (filter.mine || !user.has(Permissions.APPROVAL_DECIDE)) user.effectiveUserId else null
        val items = approvals.list(filter.status, requesterId, query.offset, query.limit, query.ascending)
        return ApprovalPage(items.map(views::approval), approvals.count(filter.status, requesterId))
    }

    fun get(user: LoginUser, approvalId: Long): ApprovalResponse = views.approval(visible(user, approvalId))

    fun submitByHuman(user: LoginUser, command: ApprovalSubmitCommand, idempotencyKey: String?): ApprovalResponse {
        val toolName = command.toolName?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "툴 이름 toolName 이 필요합니다.")
        val arguments = command.arguments ?: emptyMap()
        val scope = (arguments["channelCode"] as? String)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        return submit(
            user,
            toolName,
            arguments,
            command.reason,
            SubmitSource(user.effectiveUserId, user.name, ActorType.HUMAN, command.conversationId, scope, idempotencyKey),
        )
    }

    fun submitByService(user: LoginUser, request: ApprovalCreateRequest): ApprovalResponse {
        if (!user.service) throw BusinessException(ErrorCode.PERMISSION_DENIED, "서비스만 부를 수 있는 주소입니다.")
        val requesterId = user.onBehalfOf
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "누구 대신 요청하는지 알 수 없습니다.")
        val type = ActorType.entries.firstOrNull { it.name == request.requesterType.uppercase() && it != ActorType.SYSTEM }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "요청자 유형은 HUMAN 이나 AGENT 여야 합니다.")
        return submit(
            user,
            request.toolName,
            request.arguments,
            request.reason,
            SubmitSource(
                requesterId,
                request.requesterName,
                type,
                request.conversationId,
                request.scopeChannel?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                request.idempotencyKey,
            ),
        )
    }

    fun approve(user: LoginUser, approvalId: Long, command: ApprovalDecisionCommand): ApprovalResponse {
        SecurityFrameworkUtils.requirePermission(user, Permissions.APPROVAL_DECIDE)
        val decided = requireNotNull(
            transactions.execute {
                val request = decidable(user, approvalId)
                val now = LocalDateTime.now(clock)
                if (!approvals.decide(approvalId, ApprovalStatus.PENDING, ApprovalStatus.APPROVED, user.effectiveUserId, comment(command, false), now)) {
                    throw BusinessException(ErrorCode.INVALID_STATE, "이미 처리된 승인 요청입니다.")
                }
                audit.record(
                    user,
                    action = "approval.approve",
                    result = AuditResult.SUCCESS,
                    resourceType = RESOURCE_TYPE,
                    resourceId = approvalId.toString(),
                    approvalId = approvalId,
                )
                request
            },
        )
        log.info("승인했습니다. 승인 요청={} 툴={} 승인자={}", approvalId, decided.toolName, user.effectiveUserId)
        return execute(user, decided)
    }

    fun reject(user: LoginUser, approvalId: Long, command: ApprovalDecisionCommand): ApprovalResponse {
        SecurityFrameworkUtils.requirePermission(user, Permissions.APPROVAL_DECIDE)
        val reason = comment(command, true)
        transactions.executeWithoutResult {
            decidable(user, approvalId)
            if (!approvals.decide(approvalId, ApprovalStatus.PENDING, ApprovalStatus.REJECTED, user.effectiveUserId, reason, LocalDateTime.now(clock))) {
                throw BusinessException(ErrorCode.INVALID_STATE, "이미 처리된 승인 요청입니다.")
            }
            audit.record(
                user,
                action = "approval.reject",
                result = AuditResult.SUCCESS,
                resourceType = RESOURCE_TYPE,
                resourceId = approvalId.toString(),
                reason = reason,
                approvalId = approvalId,
            )
        }
        log.info("반려했습니다. 승인 요청={} 반려한 사람={}", approvalId, user.effectiveUserId)
        return views.approval(requireApproval(approvalId))
    }

    fun expireDue(): Int {
        val now = LocalDateTime.now(clock)
        var expired = 0
        approvals.findExpired(now, EXPIRE_BATCH).forEach { id ->
            transactions.executeWithoutResult {
                if (approvals.decide(id, ApprovalStatus.PENDING, ApprovalStatus.EXPIRED, null, null, now)) {
                    audit.record(
                        AuditEvent(
                            traceId = TraceIds.current(),
                            actorType = ActorType.SYSTEM,
                            actorId = SYSTEM_ACTOR,
                            service = SERVICE_NAME,
                            action = "approval.expire",
                            resourceType = RESOURCE_TYPE,
                            resourceId = id.toString(),
                            result = AuditResult.SUCCESS,
                            approvalId = id,
                        ),
                    )
                    expired++
                }
            }
        }
        if (expired > 0) log.info("기다리는 시간이 지난 승인 요청 {}건을 만료로 바꿨습니다.", expired)
        return expired
    }

    private fun submit(
        user: LoginUser,
        toolName: String,
        arguments: Map<String, Any?>,
        reason: String?,
        source: SubmitSource,
    ): ApprovalResponse {
        val policy = policies.find(toolName) ?: throw BusinessException(ErrorCode.NOT_FOUND, "없는 툴입니다: $toolName")
        if (!policy.enabled) throw BusinessException(ErrorCode.TOOL_DISABLED)
        if (!policy.needsApproval) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "승인을 거치지 않아도 되는 툴입니다: $toolName")
        }
        SecurityFrameworkUtils.requirePermission(user, policy.requiredPermission)
        source.scopeChannel?.let { SecurityFrameworkUtils.requireChannel(user, it) }
        val trimmedReason = reason?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > MAX_TEXT) throw BusinessException(ErrorCode.VALIDATION_FAILED, "이유는 ${MAX_TEXT}자를 넘을 수 없습니다.")
        }
        val key = source.idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }?.let { "${source.requesterId}:$it".take(MAX_KEY) }
            ?: UUID.randomUUID().toString()
        approvals.findByIdempotencyKey(key)?.let { return views.approval(it) }
        val now = LocalDateTime.now(clock)
        val ttl = policy.approvalTtlMinutes.takeIf { it > 0 } ?: properties.defaultApprovalTtlMinutes
        val draft = ApprovalRequest(
            id = null,
            toolName = policy.toolName,
            argumentsJson = views.write(arguments),
            riskLevel = policy.riskLevel,
            requesterId = source.requesterId,
            requesterName = source.requesterName,
            requesterType = source.requesterType,
            conversationId = source.conversationId,
            scopeChannel = source.scopeChannel,
            reason = trimmedReason,
            status = ApprovalStatus.PENDING,
            approverId = null,
            decisionComment = null,
            decidedAt = null,
            expiresAt = now.plusMinutes(ttl.toLong()),
            idempotencyKey = key,
            resultJson = null,
            traceId = TraceIds.current(),
            createdAt = now,
        )
        val id = try {
            requireNotNull(
                transactions.execute {
                    val saved = approvals.insert(draft)
                    audit.record(
                        user,
                        action = "approval.request",
                        result = AuditResult.PENDING,
                        resourceType = RESOURCE_TYPE,
                        resourceId = saved.toString(),
                        afterValue = mapOf("toolName" to policy.toolName, "arguments" to arguments),
                        reason = trimmedReason,
                        approvalId = saved,
                    )
                    saved
                },
            )
        } catch (exception: DuplicateKeyException) {
            return views.approval(approvals.findByIdempotencyKey(key) ?: throw exception)
        }
        log.info("승인 요청을 올렸습니다. 승인 요청={} 툴={} 요청자={}", id, policy.toolName, source.requesterId)
        return views.approval(requireApproval(id))
    }

    private fun execute(approver: LoginUser, request: ApprovalRequest): ApprovalResponse {
        val id = request.requireId()
        val outcome = runCatching { runTool(request) }
        val (status, result) = outcome.fold(
            onSuccess = { body ->
                val ok = body["ok"] != false
                (if (ok) ApprovalStatus.EXECUTED else ApprovalStatus.FAILED) to body
            },
            onFailure = { error -> ApprovalStatus.FAILED to failure(error) },
        )
        transactions.executeWithoutResult {
            if (!approvals.finish(id, status, views.write(result))) {
                throw BusinessException(ErrorCode.INVALID_STATE, "실행 결과를 남기지 못했습니다. 상태가 이미 바뀌었습니다.")
            }
            audit.record(
                approver,
                action = "approval.execute",
                result = if (status == ApprovalStatus.EXECUTED) AuditResult.SUCCESS else AuditResult.FAILED,
                resourceType = RESOURCE_TYPE,
                resourceId = id.toString(),
                afterValue = result,
                reason = if (status == ApprovalStatus.FAILED) result["message"]?.toString() else null,
                approvalId = id,
            )
        }
        if (status == ApprovalStatus.EXECUTED) {
            log.info("승인한 작업을 실행했습니다. 승인 요청={} 툴={}", id, request.toolName)
        } else {
            log.warn("승인한 작업을 실행하지 못했습니다. 승인 요청={} 툴={} 이유={}", id, request.toolName, result["message"])
        }
        return views.approval(requireApproval(id))
    }

    private fun runTool(request: ApprovalRequest): Map<String, Any?> {
        val id = request.requireId()
        val policy = policies.find(request.toolName)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "툴 정책이 사라졌습니다: ${request.toolName}")
        if (!policy.enabled) throw BusinessException(ErrorCode.TOOL_DISABLED)
        val token = tokens.issueFor(request.requesterId, id)
        requireStillAllowed(policy, request, token.perms, token.scopes)
        return executor.execute(
            token.accessToken,
            ApprovalExecuteRequest(id, request.toolName, views.readMap(request.argumentsJson) ?: emptyMap(), "approval:$id"),
        )
    }

    private fun requireStillAllowed(policy: ToolPolicy, request: ApprovalRequest, perms: List<String>, scopes: List<String>) {
        if (policy.requiredPermission !in perms) {
            throw BusinessException(ErrorCode.PERMISSION_DENIED, "요청자에게 지금은 ${policy.requiredPermission} 권한이 없습니다.")
        }
        request.scopeChannel?.let {
            if (it !in scopes) throw BusinessException(ErrorCode.SCOPE_DENIED, "요청자가 지금은 $it 채널을 맡고 있지 않습니다.")
        }
    }

    private fun failure(error: Throwable): Map<String, Any?> {
        val code = (error as? BusinessException)?.code ?: ErrorCode.UPSTREAM_FAILED
        val message = (error as? BusinessException)?.message ?: "실행을 맡은 서비스 호출에 실패했습니다."
        if (error !is BusinessException) log.warn("승인 실행 중 연동 오류가 났습니다.", error)
        return mapOf("ok" to false, "code" to code.name, "message" to message)
    }

    private fun decidable(user: LoginUser, approvalId: Long): ApprovalRequest {
        val request = requireApproval(approvalId)
        if (request.status != ApprovalStatus.PENDING) {
            throw BusinessException(ErrorCode.INVALID_STATE, "대기 중인 승인 요청만 결정할 수 있습니다. 지금 상태는 ${request.status.name} 입니다.")
        }
        if (request.expiredAt(LocalDateTime.now(clock))) {
            throw BusinessException(ErrorCode.INVALID_STATE, "기다리는 시간이 지난 승인 요청입니다.")
        }
        if (request.requesterId == user.effectiveUserId) throw BusinessException(ErrorCode.SELF_APPROVAL)
        request.scopeChannel?.let { SecurityFrameworkUtils.requireChannel(user, it) }
        return request
    }

    private fun visible(user: LoginUser, approvalId: Long): ApprovalRequest {
        val request = requireApproval(approvalId)
        if (!user.has(Permissions.APPROVAL_DECIDE) && request.requesterId != user.effectiveUserId) {
            throw BusinessException(ErrorCode.NOT_FOUND, "승인 요청을 찾을 수 없습니다.")
        }
        return request
    }

    private fun comment(command: ApprovalDecisionCommand, required: Boolean): String? {
        val value = command.comment?.trim()?.takeIf { it.isNotEmpty() }
        if (required && value == null) throw BusinessException(ErrorCode.VALIDATION_FAILED, "반려 의견 comment 가 필요합니다.")
        if (value != null && value.length > MAX_TEXT) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "의견은 ${MAX_TEXT}자를 넘을 수 없습니다.")
        }
        return value
    }

    private fun requireApproval(approvalId: Long): ApprovalRequest =
        approvals.find(approvalId) ?: throw BusinessException(ErrorCode.NOT_FOUND, "승인 요청을 찾을 수 없습니다.")

    companion object {
        private const val RESOURCE_TYPE = "approval_request"
        private const val SYSTEM_ACTOR = "governance-scheduler"
        private const val SERVICE_NAME = "governance"
        private const val EXPIRE_BATCH = 100
        private const val MAX_TEXT = 2000
        private const val MAX_KEY = 100
    }
}
