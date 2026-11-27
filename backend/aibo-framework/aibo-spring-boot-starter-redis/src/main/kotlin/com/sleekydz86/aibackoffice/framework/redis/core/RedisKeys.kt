package com.sleekydz86.aibackoffice.framework.redis.core

object RedisKeys {
    private const val PREFIX = "aibo"

    fun revokedToken(tokenId: String) = "$PREFIX:auth:revoked:$tokenId"

    fun permissionVersion(userId: String) = "$PREFIX:auth:perm-version:$userId"

    fun loginFailure(email: String) = "$PREFIX:auth:login-fail:${email.lowercase()}"

    fun rateLimit(name: String, subject: String, windowStartEpochSecond: Long) =
        "$PREFIX:rate:$name:$subject:$windowStartEpochSecond"

    fun toolPolicy(toolName: String) = "$PREFIX:mcp:tool-policy:$toolName"
}
