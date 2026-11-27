package com.sleekydz86.contentmeta.migration.domain;

import java.util.List;

/** 대조 결과. clean은 누락·불일치·고아가 모두 0인지 */
public record VerifyResult(long runId, int missing, int mismatch, int orphan, List<VerifyTableRow> tables, boolean clean) {

    public static VerifyResult of(long runId, int missing, int mismatch, int orphan, List<VerifyTableRow> tables) {
        return new VerifyResult(runId, missing, mismatch, orphan, tables, missing + mismatch + orphan == 0);
    }
}
