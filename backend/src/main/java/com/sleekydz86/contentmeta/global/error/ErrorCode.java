package com.sleekydz86.contentmeta.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C404", "요청한 주소를 찾을 수 없습니다."),
    MIGRATION_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "이관 작업을 찾을 수 없습니다."),
    MIGRATION_MODE(HttpStatus.CONFLICT, "M002", "이 작업에서 할 수 없는 이관 단계입니다."),
    MIGRATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "M003", "이관 묶음 처리에 실패했습니다. 작업 상세에서 원인을 확인하고 다시 실행하면 체크포인트에서 이어 갑니다."),
    READ_SWITCH_BLOCKED(HttpStatus.CONFLICT, "M004", "읽기 원천을 바꿀 수 없습니다."),
    READ_SOURCE_LEGACY(HttpStatus.NOT_FOUND, "M005", "아직 신규 메타로 읽기를 전환하지 않았습니다."),
    TITLE_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "작품을 찾을 수 없습니다."),
    TMDB_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "D001", "TMDB API 키가 설정되지 않았습니다. 환경변수 TMDB_API_KEY를 넣고 서버를 다시 띄우세요."),
    TMDB_NOT_LINKED(HttpStatus.NOT_FOUND, "D002", "이 작품에 연결된 TMDB ID가 없습니다."),
    TMDB_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "D003", "TMDB 응답을 받지 못했습니다."),
    BATCH_SKIPPED(HttpStatus.CONFLICT, "B001", "배치를 시작하지 않았습니다."),
    PROCEDURE_FAILED(HttpStatus.CONFLICT, "P001", "프로시저 처리에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
