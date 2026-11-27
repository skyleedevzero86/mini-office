package com.sleekydz86.contentmeta.catalog.application;

/** 언어별 제목·줄거리 등록·수정·삭제 (operation: C, U, D) */
public record LocalizationCommand(String operation, long titleId, String langCode, String localTitle, String overview) {
}
