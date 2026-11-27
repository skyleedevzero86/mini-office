package com.sleekydz86.aibackoffice.auth.application

import com.sleekydz86.aibackoffice.auth.domain.User
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.RefreshTokenRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.RoleRepository
import com.sleekydz86.aibackoffice.auth.infrastructure.persistence.UserRepository
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.redis.core.RedisTokenRevocationChecker
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserAdminService(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val revocation: RedisTokenRevocationChecker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun users(): List<UserView> = users.findAll().sortedBy { it.id }.map(UserView::from)

    @Transactional(readOnly = true)
    fun roles(): List<RoleView> = roles.findAll().sortedBy { it.id }
        .map { role -> RoleView(role.code, role.name, role.permissions.map { it.code }.sorted()) }

    @Transactional
    fun replaceRoles(userId: Long, roleCodes: List<String>): UserView {
        val user = requireUser(userId)
        val codes = roleCodes.distinct()
        val found = roles.findByCodeIn(codes)
        if (found.size != codes.size) {
            val missing = codes - found.map { it.code }.toSet()
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "없는 역할 코드가 있습니다: ${missing.joinToString(", ")}")
        }
        user.replaceRoles(found)
        invalidateTokens(user)
        log.info("역할을 바꿨습니다. 사용자={} 역할={}", userId, codes)
        return UserView.from(user)
    }

    @Transactional
    fun replaceChannels(userId: Long, channels: List<String>): UserView {
        val user = requireUser(userId)
        user.replaceChannels(channels.map { it.trim().uppercase() }.filter { it.isNotEmpty() })
        invalidateTokens(user)
        log.info("담당 채널을 바꿨습니다. 사용자={} 채널={}", userId, channels)
        return UserView.from(user)
    }

    private fun invalidateTokens(user: User) {
        refreshTokens.revokeAllByUserId(user.requireId(), LocalDateTime.now())
        revocation.bumpPermissionVersion(user.requireId().toString())
    }

    private fun requireUser(userId: Long): User =
        users.findById(userId).orElse(null) ?: throw BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
}
