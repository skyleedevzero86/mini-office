package com.sleekydz86.contentmeta.catalog.infrastructure;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 작품 한 건 (content_title) */
public class TitleRow {

    private long titleId;
    private String titleType = "";
    private String originalTitle = "";
    private String originalLanguage;
    private LocalDate releaseDate;
    private Integer runtimeMin;
    private String status = "";
    private boolean animation;    // is_animation을 별칭으로 받는다
    private BigDecimal popularity;
    private BigDecimal voteAverage;
    private Integer voteCount;
    private String posterPath;
    private int metaVersion;

    public long getTitleId() {
        return titleId;
    }

    public void setTitleId(long titleId) {
        this.titleId = titleId;
    }

    public String getTitleType() {
        return titleType;
    }

    public void setTitleType(String titleType) {
        this.titleType = titleType;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public void setOriginalLanguage(String originalLanguage) {
        this.originalLanguage = originalLanguage;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Integer getRuntimeMin() {
        return runtimeMin;
    }

    public void setRuntimeMin(Integer runtimeMin) {
        this.runtimeMin = runtimeMin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAnimation() {
        return animation;
    }

    public void setAnimation(boolean animation) {
        this.animation = animation;
    }

    public BigDecimal getPopularity() {
        return popularity;
    }

    public void setPopularity(BigDecimal popularity) {
        this.popularity = popularity;
    }

    public BigDecimal getVoteAverage() {
        return voteAverage;
    }

    public void setVoteAverage(BigDecimal voteAverage) {
        this.voteAverage = voteAverage;
    }

    public Integer getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(Integer voteCount) {
        this.voteCount = voteCount;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public int getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(int metaVersion) {
        this.metaVersion = metaVersion;
    }

}
