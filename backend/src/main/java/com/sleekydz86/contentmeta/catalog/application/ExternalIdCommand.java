package com.sleekydz86.contentmeta.catalog.application;

/** 외부 표준 ID 연결·변경·해제 (operation: C, U, D) */
public record ExternalIdCommand(String operation, String entityType, long entityId, String provider, String externalId) {
}
