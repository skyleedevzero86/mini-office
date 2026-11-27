package com.sleekydz86.aibackoffice.framework.file.core

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.file.config.AiboFileProperties
import java.util.UUID

class FileRules(private val properties: AiboFileProperties) {

    fun check(fileName: String?, contentType: String?, sizeBytes: Long): CheckedFile {
        val name = safeName(fileName)
        if (sizeBytes <= 0) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "빈 파일은 올릴 수 없습니다.")
        }
        if (sizeBytes > properties.maxSizeBytes) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "파일은 ${properties.maxSizeBytes / BYTES_PER_MB}메가바이트를 넘을 수 없습니다.")
        }
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: throw BusinessException(ErrorCode.VALIDATION_FAILED, "파일 종류를 알 수 없습니다.")
        if (type !in properties.allowedContentTypes) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED, "올릴 수 없는 파일 종류입니다: $type")
        }
        return CheckedFile(name, type, sizeBytes)
    }

    fun key(prefix: String, fileName: String): String =
        "${prefix.trim('/')}/${UUID.randomUUID().toString().replace("-", "")}-$fileName"

    companion object {
        private const val BYTES_PER_MB = 1024 * 1024
        private const val MAX_NAME = 150
        private val UNSAFE = Regex("[^A-Za-z0-9가-힣._-]")

        fun safeName(fileName: String?): String {
            val base = fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
            val cleaned = base.replace(UNSAFE, "_").trimStart('.').take(MAX_NAME)
            if (cleaned.isEmpty() || cleaned.all { it == '_' }) {
                throw BusinessException(ErrorCode.VALIDATION_FAILED, "파일 이름이 올바르지 않습니다.")
            }
            return cleaned
        }
    }
}

data class CheckedFile(val fileName: String, val contentType: String, val sizeBytes: Long)
