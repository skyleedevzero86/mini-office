package com.sleekydz86.aibackoffice.framework.redis.core

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.redis.core.StringRedisTemplate

class RedisRateLimiter(
    private val redis: ObjectProvider<StringRedisTemplate>,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun tryAcquire(name: String, subject: String, limit: Int, window: Duration): Boolean {
        val windowSeconds = window.seconds.coerceAtLeast(1)
        val windowStart = clock.instant().epochSecond / windowSeconds * windowSeconds
        val key = RedisKeys.rateLimit(name, subject, windowStart)
        val operations = redis.getObject().opsForValue()
        val count = operations.increment(key) ?: 1L
        if (count == 1L) redis.getObject().expire(key, Duration.ofSeconds(windowSeconds))
        return count <= limit
    }

    fun acquireOrThrow(name: String, subject: String, limit: Int, window: Duration) {
        if (!tryAcquire(name, subject, limit, window)) throw BusinessException(ErrorCode.TOO_MANY_REQUESTS)
    }
}
