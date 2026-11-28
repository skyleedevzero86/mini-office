package com.sleekydz86.contentmeta.migration.infrastructure;

import java.time.LocalDateTime;

public class LatestVerifyRow {

    private long runId;
    private LocalDateTime finishedAt;
    private long ageMinutes;
    private int missing;
    private int mismatch;
    private int orphan;

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getAgeMinutes() {
        return ageMinutes;
    }

    public void setAgeMinutes(long ageMinutes) {
        this.ageMinutes = ageMinutes;
    }

    public int getMissing() {
        return missing;
    }

    public void setMissing(int missing) {
        this.missing = missing;
    }

    public int getMismatch() {
        return mismatch;
    }

    public void setMismatch(int mismatch) {
        this.mismatch = mismatch;
    }

    public int getOrphan() {
        return orphan;
    }

    public void setOrphan(int orphan) {
        this.orphan = orphan;
    }

    public boolean isClean() {
        return missing + mismatch + orphan == 0;
    }

}
