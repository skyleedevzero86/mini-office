package com.sleekydz86.aibackoffice.framework.web.core.handler

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.ErrorResponse
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(-1)
class DataAccessExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun optimisticLock(exception: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> =
        GlobalExceptionHandler.respond(ErrorCode.CONFLICT, "다른 사람이 먼저 고쳤습니다. 새로 불러온 뒤 다시 시도하세요.")

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integrity(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        GlobalExceptionHandler.respond(ErrorCode.CONFLICT, "이미 있는 값이거나 연결된 데이터가 있어 저장하지 못했습니다.")
}
