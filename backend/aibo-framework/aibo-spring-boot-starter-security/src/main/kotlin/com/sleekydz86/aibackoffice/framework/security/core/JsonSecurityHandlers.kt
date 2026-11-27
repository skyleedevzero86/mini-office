package com.sleekydz86.aibackoffice.framework.security.core

import com.sleekydz86.aibackoffice.common.ErrorCode
import com.sleekydz86.aibackoffice.common.ErrorResponse
import com.sleekydz86.aibackoffice.framework.web.core.handler.GlobalExceptionHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.json.JsonMapper

class JsonAuthenticationEntryPoint(private val jsonMapper: JsonMapper) : AuthenticationEntryPoint {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        write(response, jsonMapper, GlobalExceptionHandler.body(ErrorCode.UNAUTHORIZED), ErrorCode.UNAUTHORIZED.status)
    }
}

class JsonAccessDeniedHandler(private val jsonMapper: JsonMapper) : AccessDeniedHandler {

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        write(response, jsonMapper, GlobalExceptionHandler.body(ErrorCode.PERMISSION_DENIED), ErrorCode.PERMISSION_DENIED.status)
    }
}

@RestControllerAdvice
@Order(-1)
class SecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDenied(exception: AccessDeniedException): ResponseEntity<ErrorResponse> =
        GlobalExceptionHandler.respond(ErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED.defaultMessage)
}

private fun write(response: HttpServletResponse, jsonMapper: JsonMapper, body: ErrorResponse, status: Int) {
    response.status = status
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.characterEncoding = Charsets.UTF_8.name()
    response.writer.write(jsonMapper.writeValueAsString(body))
}
