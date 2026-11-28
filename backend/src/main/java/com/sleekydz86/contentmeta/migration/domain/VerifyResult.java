package com.sleekydz86.contentmeta.migration.domain;

import java.util.List;

public record VerifyResult(long runId, int missing, int mismatch, int orphan, List<VerifyTableRow> tables, boolean clean) {

    public static VerifyResult of(long runId, int missing, int mismatch, int orphan, List<VerifyTableRow> tables) {
        return new VerifyResult(runId, missing, mismatch, orphan, tables, missing + mismatch + orphan == 0);
    }
}
