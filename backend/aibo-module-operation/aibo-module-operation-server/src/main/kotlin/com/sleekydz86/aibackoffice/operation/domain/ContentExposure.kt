package com.sleekydz86.aibackoffice.operation.domain

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import java.time.LocalDateTime

data class ContentExposure(
    val contentId: Long,
    val exposed: Boolean,
    val reason: String?,
    val issueId: Long?,
    val approvalId: Long?,
    val updatedBy: String?,
    val updatedAt: LocalDateTime?,
    val version: Int,
) {
    fun alreadyDone(targetExposed: Boolean, approvalId: Long): Boolean =
        exposed == targetExposed && this.approvalId == approvalId

    fun hide(issueId: Long, reason: String, approvalId: Long, by: String, at: LocalDateTime): ContentExposure {
        if (!exposed) throw BusinessException(ErrorCode.INVALID_STATE, "이미 숨김 처리된 작품입니다.")
        return copy(exposed = false, reason = reason, issueId = issueId, approvalId = approvalId, updatedBy = by, updatedAt = at)
    }

    fun restore(reason: String, approvalId: Long, by: String, at: LocalDateTime): ContentExposure {
        if (exposed) throw BusinessException(ErrorCode.INVALID_STATE, "숨김 처리되지 않은 작품입니다.")
        return copy(exposed = true, reason = reason, approvalId = approvalId, updatedBy = by, updatedAt = at)
    }

    companion object {
        fun visible(contentId: Long): ContentExposure =
            ContentExposure(contentId, true, null, null, null, null, null, 0)
    }
}
