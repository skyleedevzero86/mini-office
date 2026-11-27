package com.sleekydz86.aibackoffice.framework.web.core.handler

import com.sleekydz86.aibackoffice.common.BusinessException
import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.ErrorResponse
import com.sleekydz86.aibackoffice.framework.web.core.trace.TraceIds
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
@Order(0)
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun business(exception: BusinessException): ResponseEntity<ErrorResponse> {
        if (exception.code.status >= 500) {
            log.warn("업무 처리 중 서버 오류가 났습니다. 코드={}", exception.code.name, exception)
        } else {
            log.info("업무 규칙에 맞지 않는 요청입니다. 코드={} 내용={}", exception.code.name, exception.message)
        }
        return respond(exception.code, exception.message ?: exception.code.defaultMessage)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail = exception.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return respond(ErrorCode.VALIDATION_FAILED, "${ErrorCode.VALIDATION_FAILED.defaultMessage} $detail")
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun invalidParameter(exception: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val detail = exception.constraintViolations.joinToString(", ") { "${it.propertyPath}: ${it.message}" }
        return respond(ErrorCode.VALIDATION_FAILED, "${ErrorCode.VALIDATION_FAILED.defaultMessage} $detail")
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(exception: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.VALIDATION_FAILED, "필수 값이 빠졌습니다: ${exception.parameterName}")

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(exception: MissingRequestHeaderException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.VALIDATION_FAILED, "필수 헤더가 빠졌습니다: ${exception.headerName}")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.VALIDATION_FAILED, "값 형식이 올바르지 않습니다: ${exception.name}")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(exception: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.VALIDATION_FAILED, "요청 본문을 읽을 수 없습니다.")

    @ExceptionHandler(NoResourceFoundException::class)
    fun noResource(exception: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(exception: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.NOT_FOUND, "지원하지 않는 요청 방식입니다: ${exception.method}")

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        log.error("예상하지 못한 오류가 났습니다.", exception)
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage)
    }

    companion object {
        fun body(code: ErrorCode, message: String = code.defaultMessage): ErrorResponse =
            ErrorResponse(code.name, message, TraceIds.current())

        fun respond(code: ErrorCode, message: String): ResponseEntity<ErrorResponse> =
            ResponseEntity.status(code.status).body(body(code, message))
    }
}
