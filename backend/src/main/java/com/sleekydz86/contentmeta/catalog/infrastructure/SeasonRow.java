package com.sleekydz86.contentmeta.catalog.infrastructure;

import java.time.LocalDate;

public class SeasonRow {

    private long seasonId;
    private int seasonNo;
    private String seasonName;
    private LocalDate airDate;
    private Integer episodeCount;
    private int loadedEpisodes;

    public long getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(long seasonId) {
        this.seasonId = seasonId;
    }

    public int getSeasonNo() {
        return seasonNo;
    }

    public void setSeasonNo(int seasonNo) {
        this.seasonNo = seasonNo;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public void setSeasonName(String seasonName) {
        this.seasonName = seasonName;
    }

    public LocalDate getAirDate() {
        return airDate;
    }

    public void setAirDate(LocalDate airDate) {
        this.airDate = airDate;
    }

    public Integer getEpisodeCount() {
        return episodeCount;
    }

    public void setEpisodeCount(Integer episodeCount) {
        this.episodeCount = episodeCount;
    }

    public int getLoadedEpisodes() {
        return loadedEpisodes;
    }

    public void setLoadedEpisodes(int loadedEpisodes) {
        this.loadedEpisodes = loadedEpisodes;
    }

}
