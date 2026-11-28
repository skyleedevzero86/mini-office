package com.sleekydz86.contentmeta.operation.infrastructure;

import java.time.LocalDateTime;

public class BatchRunRow {

    private long jobRunId;
    private String jobName = "";
    private String status = "";
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int processedCount;
    private String errorMessage;
    private boolean slaBreached;
    private String worker = "";

    public long getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isSlaBreached() {
        return slaBreached;
    }

    public void setSlaBreached(boolean slaBreached) {
        this.slaBreached = slaBreached;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

}
