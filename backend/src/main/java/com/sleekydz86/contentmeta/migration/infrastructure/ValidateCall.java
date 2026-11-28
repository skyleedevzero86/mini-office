package com.sleekydz86.contentmeta.migration.infrastructure;

public class ValidateCall {

    private long runId;
    private Integer errorCount = 0;
    private Integer infoCount = 0;

    public ValidateCall() {
    }

    public ValidateCall(long runId) {
        this.runId = runId;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public Integer getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(Integer infoCount) {
        this.infoCount = infoCount;
    }

}
