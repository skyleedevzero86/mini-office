package com.sleekydz86.contentmeta.operation.infrastructure;

import java.time.LocalDateTime;

/** 작업별 SLA 신선도 (OK | STALE) */
public class SlaFreshnessRow {

    private String jobName = "";
    private int maxGapMinutes;
    private LocalDateTime lastSuccessAt;
    private Long minutesSinceSuccess;
    private int failures24h;
    private int slaBreaches24h;
    private String freshness = "";

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public int getMaxGapMinutes() {
        return maxGapMinutes;
    }

    public void setMaxGapMinutes(int maxGapMinutes) {
        this.maxGapMinutes = maxGapMinutes;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public Long getMinutesSinceSuccess() {
        return minutesSinceSuccess;
    }

    public void setMinutesSinceSuccess(Long minutesSinceSuccess) {
        this.minutesSinceSuccess = minutesSinceSuccess;
    }

    public int getFailures24h() {
        return failures24h;
    }

    public void setFailures24h(int failures24h) {
        this.failures24h = failures24h;
    }

    public int getSlaBreaches24h() {
        return slaBreaches24h;
    }

    public void setSlaBreaches24h(int slaBreaches24h) {
        this.slaBreaches24h = slaBreaches24h;
    }

    public String getFreshness() {
        return freshness;
    }

    public void setFreshness(String freshness) {
        this.freshness = freshness;
    }

}
