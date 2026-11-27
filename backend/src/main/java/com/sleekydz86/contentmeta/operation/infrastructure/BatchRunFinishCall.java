package com.sleekydz86.contentmeta.operation.infrastructure;

/** sp_batch_run_finish 호출 (OUT: SLA 위반 여부) */
public class BatchRunFinishCall {

    private long jobRunId;
    private int success;
    private int processed;
    private String error;
    private Integer slaBreached = 0;

    public BatchRunFinishCall() {
    }

    public BatchRunFinishCall(long jobRunId, int success, int processed, String error) {
        this.jobRunId = jobRunId;
        this.success = success;
        this.processed = processed;
        this.error = error;
    }

    public long getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getSlaBreached() {
        return slaBreached;
    }

    public void setSlaBreached(Integer slaBreached) {
        this.slaBreached = slaBreached;
    }

}
