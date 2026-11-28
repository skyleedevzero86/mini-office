package com.sleekydz86.contentmeta.migration.infrastructure;

import java.time.LocalDateTime;

public class MigrationRunRow {

    private Long runId;
    private String mode = "";
    private String status = "";
    private String description;
    private int batchSize = 500;
    private int lastMovieSeq;
    private int lastTvSeq;
    private int lastCartoonSeq;
    private int batchCount;
    private int attemptCount;
    private int errorCount;
    private int infoCount;
    private int upsertedTitles;
    private int upsertedCredits;
    private int upsertedEpisodes;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public MigrationRunRow() {
    }

    public MigrationRunRow(String mode, String description, int batchSize) {
        this.mode = mode;
        this.description = description;
        this.batchSize = batchSize;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getLastMovieSeq() {
        return lastMovieSeq;
    }

    public void setLastMovieSeq(int lastMovieSeq) {
        this.lastMovieSeq = lastMovieSeq;
    }

    public int getLastTvSeq() {
        return lastTvSeq;
    }

    public void setLastTvSeq(int lastTvSeq) {
        this.lastTvSeq = lastTvSeq;
    }

    public int getLastCartoonSeq() {
        return lastCartoonSeq;
    }

    public void setLastCartoonSeq(int lastCartoonSeq) {
        this.lastCartoonSeq = lastCartoonSeq;
    }

    public int getBatchCount() {
        return batchCount;
    }

    public void setBatchCount(int batchCount) {
        this.batchCount = batchCount;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public void setInfoCount(int infoCount) {
        this.infoCount = infoCount;
    }

    public int getUpsertedTitles() {
        return upsertedTitles;
    }

    public void setUpsertedTitles(int upsertedTitles) {
        this.upsertedTitles = upsertedTitles;
    }

    public int getUpsertedCredits() {
        return upsertedCredits;
    }

    public void setUpsertedCredits(int upsertedCredits) {
        this.upsertedCredits = upsertedCredits;
    }

    public int getUpsertedEpisodes() {
        return upsertedEpisodes;
    }

    public void setUpsertedEpisodes(int upsertedEpisodes) {
        this.upsertedEpisodes = upsertedEpisodes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

}
