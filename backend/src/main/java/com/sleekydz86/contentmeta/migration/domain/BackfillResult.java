package com.sleekydz86.contentmeta.migration.domain;

public record BackfillResult(long runId, int chunks, int titles, int errors, boolean done, long elapsedMs) {
}
