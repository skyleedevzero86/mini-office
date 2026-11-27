package com.sleekydz86.contentmeta.operation.application;

/** 배치 한 번의 결과. skipped=true면 같은 작업이 이미 돌고 있어 시작하지 않은 것 */
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
