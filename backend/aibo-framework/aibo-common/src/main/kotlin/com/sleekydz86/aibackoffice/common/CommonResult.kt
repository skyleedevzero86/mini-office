package com.sleekydz86.aibackoffice.common

import com.fasterxml.jackson.annotation.JsonIgnore

data class CommonResult<T>(
    val code: String,
    val message: String,
    val data: T?,
) {
    @get:JsonIgnore
    val success: Boolean
        get() = code == SUCCESS

    fun checkedData(): T {
        if (!success) {
            val errorCode = ErrorCode.entries.firstOrNull { it.name == code } ?: ErrorCode.UPSTREAM_FAILED
            throw BusinessException(errorCode, message)
        }
        return data ?: throw BusinessException(ErrorCode.UPSTREAM_FAILED, "연동 서비스가 빈 응답을 돌려주었습니다.")
    }

    companion object {
        const val SUCCESS = "OK"

        fun <T> ok(data: T): CommonResult<T> = CommonResult(SUCCESS, "성공", data)

        fun <T> error(code: ErrorCode, message: String = code.defaultMessage): CommonResult<T> =
            CommonResult(code.name, message, null)
    }
}
