package com.sleekydz86.contentmeta.migration.infrastructure;

public class VerifyCall {

    private long runId;
    private Integer missing = 0;
    private Integer mismatch = 0;
    private Integer orphan = 0;

    public VerifyCall() {
    }

    public VerifyCall(long runId) {
        this.runId = runId;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public Integer getMissing() {
        return missing;
    }

    public void setMissing(Integer missing) {
        this.missing = missing;
    }

    public Integer getMismatch() {
        return mismatch;
    }

    public void setMismatch(Integer mismatch) {
        this.mismatch = mismatch;
    }

    public Integer getOrphan() {
        return orphan;
    }

    public void setOrphan(Integer orphan) {
        this.orphan = orphan;
    }

}
