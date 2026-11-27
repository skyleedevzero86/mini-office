package com.sleekydz86.aibackoffice.framework.redis.config

import com.sleekydz86.aibackoffice.framework.redis.core.RedisRateLimiter
import com.sleekydz86.aibackoffice.framework.redis.core.RedisTokenRevocationChecker
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate

@AutoConfiguration
class AiboRedisAutoConfiguration {

    @Bean
    fun redisTokenRevocationChecker(redis: ObjectProvider<StringRedisTemplate>): RedisTokenRevocationChecker =
        RedisTokenRevocationChecker(redis)

    @Bean
    fun redisRateLimiter(redis: ObjectProvider<StringRedisTemplate>): RedisRateLimiter = RedisRateLimiter(redis)
}
