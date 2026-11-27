package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser
import com.sleekydz86.aibackoffice.operation.infrastructure.persistence.IdempotencyStore
import java.security.MessageDigest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@Service
class IdempotencyService(
    private val store: IdempotencyStore,
    private val jsonMapper: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.MANDATORY)
    fun <T : Any> execute(
        user: LoginUser,
        key: String?,
        operation: String,
        request: Any?,
        responseType: Class<T>,
        action: () -> T,
    ): T {
        val clientKey = key?.trim()?.takeIf { it.isNotEmpty() } ?: return action()
        if (clientKey.length > MAX_CLIENT_KEY_LENGTH) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "멱등 키는 ${MAX_CLIENT_KEY_LENGTH}자를 넘을 수 없습니다.")
        }
        val storedKey = "${user.effectiveUserId}:$clientKey".take(MAX_STORED_KEY_LENGTH)
        val requestHash = sha256(jsonMapper.writeValueAsString(mapOf("operation" to operation, "request" to request)))
        val previous = store.find(storedKey)
        if (previous != null) {
            if (previous.requestHash != requestHash) {
                throw BusinessException(ErrorCode.IDEMPOTENCY_MISMATCH)
            }
            log.info("같은 멱등 키로 다시 온 요청이라 처음 결과를 돌려줍니다. 작업={}", operation)
            val body = previous.body ?: throw BusinessException(ErrorCode.INTERNAL_ERROR, "저장된 처음 결과가 비어 있습니다.")
            return jsonMapper.readValue(body, responseType)
        }
        val result = action()
        store.insert(storedKey, requestHash, OK_STATUS, jsonMapper.writeValueAsString(result))
        return result
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_CLIENT_KEY_LENGTH = 64
        private const val MAX_STORED_KEY_LENGTH = 100
        private const val OK_STATUS = 200
    }
}
