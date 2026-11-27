package com.sleekydz86.aibackoffice.operation.application

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.framework.security.core.LoginUser

object ApprovedExecution {

    fun require(user: LoginUser, approvalId: Long?, message: String): Long {
        if (approvalId == null || approvalId <= 0) {
            throw BusinessException(ErrorCode.APPROVAL_REQUIRED, "$message 승인 번호 헤더 X-Approval-Id 가 필요합니다.")
        }
        if (!user.service) {
            throw BusinessException(ErrorCode.APPROVAL_REQUIRED, "$message 승인된 요청을 실행하는 서비스만 부를 수 있습니다.")
        }
        return approvalId
    }
}
