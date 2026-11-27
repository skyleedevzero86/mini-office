package com.sleekydz86.aibackoffice.operation.presentation

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.ErrorResponse
import com.sleekydz86.aibackoffice.framework.web.core.handler.GlobalExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
@Order(-1)
class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(exception: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> =
        GlobalExceptionHandler.respond(ErrorCode.VALIDATION_FAILED, "올릴 수 있는 파일 크기를 넘었습니다.")

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun missingPart(exception: MissingServletRequestPartException): ResponseEntity<ErrorResponse> =
        GlobalExceptionHandler.respond(ErrorCode.VALIDATION_FAILED, "올릴 파일이 빠졌습니다: ${exception.requestPartName}")
}
