package com.sleekydz86.aibackoffice.framework.redis.core

import com.sleekydz86.aibackoffice.framework.security.core.TokenRevocationChecker
import java.time.Duration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate

class RedisTokenRevocationChecker(private val redis: ObjectProvider<StringRedisTemplate>) : TokenRevocationChecker {

    override fun isRevoked(tokenId: String): Boolean = redis.getObject().hasKey(RedisKeys.revokedToken(tokenId))

    override fun currentPermissionVersion(userId: String): Long? =
        redis.getObject().opsForValue().get(RedisKeys.permissionVersion(userId))?.toLongOrNull()

    fun revoke(tokenId: String, remaining: Duration) {
        if (remaining.isNegative || remaining.isZero) return
        redis.getObject().opsForValue().set(RedisKeys.revokedToken(tokenId), "1", remaining)
    }

    fun bumpPermissionVersion(userId: String): Long =
        redis.getObject().opsForValue().increment(RedisKeys.permissionVersion(userId)) ?: 1L
}
