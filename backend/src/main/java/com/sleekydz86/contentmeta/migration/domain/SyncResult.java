package com.sleekydz86.contentmeta.migration.domain;

public record SyncResult(long runId, int changes, int titles, int deleted, int errors) {
}
