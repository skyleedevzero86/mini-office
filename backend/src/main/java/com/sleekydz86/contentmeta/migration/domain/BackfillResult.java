package com.sleekydz86.contentmeta.migration.domain;

/** 백필 한 번 호출의 결과. done=false면 다시 호출하면 체크포인트에서 이어 간다 */
public record BackfillResult(long runId, int chunks, int titles, int errors, boolean done, long elapsedMs) {
}
