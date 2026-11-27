package com.sleekydz86.aibackoffice.common

enum class ErrorCode(val status: Int, val defaultMessage: String) {
    VALIDATION_FAILED(400, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "로그인이 필요합니다."),
    BAD_CREDENTIALS(401, "이메일 또는 비밀번호가 맞지 않습니다."),
    PERMISSION_DENIED(403, "이 작업을 할 권한이 없습니다."),
    SCOPE_DENIED(403, "담당하는 채널 밖의 대상입니다."),
    SELF_APPROVAL(403, "자기 요청은 직접 승인할 수 없습니다."),
    APPROVAL_REQUIRED(403, "승인을 거쳐야 실행할 수 있는 작업입니다."),
    TOOL_DISABLED(403, "사용이 꺼진 툴입니다."),
    NOT_FOUND(404, "대상을 찾을 수 없습니다."),
    CONFLICT(409, "다른 변경과 충돌했습니다."),
    INVALID_STATE(409, "지금 상태에서는 할 수 없는 작업입니다."),
    IDEMPOTENCY_MISMATCH(422, "같은 멱등 키로 다른 요청이 왔습니다."),
    ACCOUNT_LOCKED(423, "잠긴 계정입니다. 잠시 뒤 다시 시도하세요."),
    TOO_MANY_REQUESTS(429, "요청이 너무 많습니다. 잠시 뒤 다시 시도하세요."),
    SERVICE_NOT_CONFIGURED(503, "서비스 설정이 끝나지 않았습니다. 환경변수를 확인하세요."),
    PENDING_APPROVAL(202, "승인 대기 중입니다."),
    UPSTREAM_FAILED(502, "연동 서비스 호출에 실패했습니다."),
    INTERNAL_ERROR(500, "처리 중 오류가 났습니다."),
}

open class BusinessException(
    val code: ErrorCode,
    message: String = code.defaultMessage,
) : RuntimeException(message)

data class ErrorResponse(val code: String, val message: String, val traceId: String?)
