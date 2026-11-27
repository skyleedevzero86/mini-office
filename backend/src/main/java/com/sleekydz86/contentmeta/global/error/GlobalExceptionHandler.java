package com.sleekydz86.contentmeta.global.error;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.status().is5xxServerError()) {
            log.warn("[{}] {}", errorCode.code(), exception.getMessage(), exception.getCause());
        } else {
            log.info("[{}] {}", errorCode.code(), exception.getMessage());
        }
        return respond(errorCode, exception.getDetail());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return respond(ErrorCode.INVALID_INPUT, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException exception) {
        return respond(ErrorCode.INVALID_INPUT, "필수 파라미터가 없습니다: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return respond(ErrorCode.INVALID_INPUT, "파라미터 형식이 올바르지 않습니다: " + exception.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        return respond(ErrorCode.INVALID_INPUT, "요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException exception) {
        return respond(ErrorCode.NOT_FOUND, exception.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("예상하지 못한 오류가 발생했습니다.", exception);
        return respond(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiError> respond(ErrorCode errorCode, String detail) {
        return ResponseEntity.status(errorCode.status()).body(new ApiError(errorCode.code(), errorCode.message(), detail));
    }
}
