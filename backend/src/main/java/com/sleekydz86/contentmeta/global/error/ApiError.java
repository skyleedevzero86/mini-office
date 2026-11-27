package com.sleekydz86.contentmeta.global.error;

/** 오류 응답 본문 (화면은 code로 분기하고 message·detail을 보여 준다) */
public record ApiError(String code, String message, String detail) {
}
