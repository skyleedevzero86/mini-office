package com.sleekydz86.contentmeta.dashboard.infrastructure;

import java.time.LocalDate;

public class BatchTrendRow {

    private LocalDate runDate;
    private String jobName = "";
    private int successCount;
    private int failedCount;
    private int slaBreachedCount;
    private Long avgDurationMs;

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getSlaBreachedCount() {
        return slaBreachedCount;
    }

    public void setSlaBreachedCount(int slaBreachedCount) {
        this.slaBreachedCount = slaBreachedCount;
    }

    public Long getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(Long avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }

}
