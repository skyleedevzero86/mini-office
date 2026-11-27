package com.sleekydz86.contentmeta.operation.infrastructure;

/** sp_run_quality_checks 호출 */
public class QualityCheckCall {

    private long jobRunId;
    private Integer failedRules = 0;

    public QualityCheckCall() {
    }

    public QualityCheckCall(long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public long getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public Integer getFailedRules() {
        return failedRules;
    }

    public void setFailedRules(Integer failedRules) {
        this.failedRules = failedRules;
    }

}
