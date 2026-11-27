package com.sleekydz86.contentmeta.dashboard.infrastructure;

import java.time.LocalDateTime;

/** 신규 메타·레거시·동기화 대기의 현재 수치 */
public class OverviewRow {

    private int totalTitles;
    private int movieCount;
    private int seriesCount;
    private int animationCount;
    private int deletedCount;
    private int seasonCount;
    private int episodeCount;
    private int personCount;
    private int nameOnlyPersonCount;
    private int titlesWithExternalId;
    private int titlesWithKo;
    private int legacyRows;
    private int pendingChanges;
    private LocalDateTime oldestPendingChangeAt;

    public int getTotalTitles() {
        return totalTitles;
    }

    public void setTotalTitles(int totalTitles) {
        this.totalTitles = totalTitles;
    }

    public int getMovieCount() {
        return movieCount;
    }

    public void setMovieCount(int movieCount) {
        this.movieCount = movieCount;
    }

    public int getSeriesCount() {
        return seriesCount;
    }

    public void setSeriesCount(int seriesCount) {
        this.seriesCount = seriesCount;
    }

    public int getAnimationCount() {
        return animationCount;
    }

    public void setAnimationCount(int animationCount) {
        this.animationCount = animationCount;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public int getSeasonCount() {
        return seasonCount;
    }

    public void setSeasonCount(int seasonCount) {
        this.seasonCount = seasonCount;
    }

    public int getEpisodeCount() {
        return episodeCount;
    }

    public void setEpisodeCount(int episodeCount) {
        this.episodeCount = episodeCount;
    }

    public int getPersonCount() {
        return personCount;
    }

    public void setPersonCount(int personCount) {
        this.personCount = personCount;
    }

    public int getNameOnlyPersonCount() {
        return nameOnlyPersonCount;
    }

    public void setNameOnlyPersonCount(int nameOnlyPersonCount) {
        this.nameOnlyPersonCount = nameOnlyPersonCount;
    }

    public int getTitlesWithExternalId() {
        return titlesWithExternalId;
    }

    public void setTitlesWithExternalId(int titlesWithExternalId) {
        this.titlesWithExternalId = titlesWithExternalId;
    }

    public int getTitlesWithKo() {
        return titlesWithKo;
    }

    public void setTitlesWithKo(int titlesWithKo) {
        this.titlesWithKo = titlesWithKo;
    }

    public int getLegacyRows() {
        return legacyRows;
    }

    public void setLegacyRows(int legacyRows) {
        this.legacyRows = legacyRows;
    }

    public int getPendingChanges() {
        return pendingChanges;
    }

    public void setPendingChanges(int pendingChanges) {
        this.pendingChanges = pendingChanges;
    }

    public LocalDateTime getOldestPendingChangeAt() {
        return oldestPendingChangeAt;
    }

    public void setOldestPendingChangeAt(LocalDateTime oldestPendingChangeAt) {
        this.oldestPendingChangeAt = oldestPendingChangeAt;
    }

}
