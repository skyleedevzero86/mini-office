package com.sleekydz86.contentmeta.migration.domain;

/** 이관 작업 모드 (migration_run.mode) */
public enum MigrationMode {
    BACKFILL("기존 데이터 묶음 적재"),
    SYNC("레거시 변경분 반영"),
    VERIFY("원본·신규 대조");

    private final String label;

    MigrationMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
