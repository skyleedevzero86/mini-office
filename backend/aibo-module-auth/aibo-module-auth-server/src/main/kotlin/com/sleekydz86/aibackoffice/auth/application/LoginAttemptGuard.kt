package com.sleekydz86.aibackoffice.auth.application

import com.sleekydz86.aibackoffice.auth.infrastructure.config.AuthProperties
import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.redis.core.RedisKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class LoginAttemptGuard(
    private val redis: StringRedisTemplate,
    private val properties: AuthProperties,
) {

    fun requireNotLocked(email: String) {
        val failures = redis.opsForValue().get(RedisKeys.loginFailure(email))?.toIntOrNull() ?: 0
        if (failures >= properties.loginFailureLimit) {
            throw BusinessException(ErrorCode.ACCOUNT_LOCKED)
        }
    }

    fun recordFailure(email: String) {
        val key = RedisKeys.loginFailure(email)
        val count = redis.opsForValue().increment(key) ?: 1L
        if (count == 1L) redis.expire(key, properties.loginLockDuration)
    }

    fun clear(email: String) {
        redis.delete(RedisKeys.loginFailure(email))
    }
}
