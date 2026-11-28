package com.sleekydz86.contentmeta.migration.infrastructure;

public class SyncCall {

    private long runId;
    private int batchSize;
    private Integer changes = 0;
    private Integer titles = 0;
    private Integer deleted = 0;
    private Integer errors = 0;

    public SyncCall() {
    }

    public SyncCall(long runId, int batchSize) {
        this.runId = runId;
        this.batchSize = batchSize;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getChanges() {
        return changes;
    }

    public void setChanges(Integer changes) {
        this.changes = changes;
    }

    public Integer getTitles() {
        return titles;
    }

    public void setTitles(Integer titles) {
        this.titles = titles;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getErrors() {
        return errors;
    }

    public void setErrors(Integer errors) {
        this.errors = errors;
    }

}
