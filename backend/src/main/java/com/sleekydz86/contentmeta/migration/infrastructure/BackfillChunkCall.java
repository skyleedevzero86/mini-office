package com.sleekydz86.contentmeta.migration.infrastructure;

/** sp_backfill_legacy_chunk 호출 (OUT: 완료 여부·작품 수·오류 수) */
public class BackfillChunkCall {

    private long runId;
    private Integer done = 0;
    private Integer titles = 0;
    private Integer errors = 0;

    public BackfillChunkCall() {
    }

    public BackfillChunkCall(long runId) {
        this.runId = runId;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public Integer getDone() {
        return done;
    }

    public void setDone(Integer done) {
        this.done = done;
    }

    public Integer getTitles() {
        return titles;
    }

    public void setTitles(Integer titles) {
        this.titles = titles;
    }

    public Integer getErrors() {
        return errors;
    }

    public void setErrors(Integer errors) {
        this.errors = errors;
    }

}
