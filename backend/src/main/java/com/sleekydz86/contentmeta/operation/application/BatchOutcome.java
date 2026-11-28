package com.sleekydz86.contentmeta.operation.application;

public record BatchOutcome(
        BatchJob job,
        Long jobRunId,
        boolean skipped,
        boolean success,
        int processed,
        boolean slaBreached,
        String message
) {
}
