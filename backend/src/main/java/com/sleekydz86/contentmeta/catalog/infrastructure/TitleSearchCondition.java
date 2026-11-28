package com.sleekydz86.contentmeta.catalog.infrastructure;

public class TitleSearchCondition {

    private String query;
    private String lang;
    private String titleType;
    private String genre;
    private String status;
    private Boolean animation;
    private String sort;
    private int offset;
    private int size;

    public TitleSearchCondition() {
    }

    public TitleSearchCondition(String query, String lang, String titleType, String genre, String status, Boolean animation, String sort, int offset, int size) {
        this.query = query;
        this.lang = lang;
        this.titleType = titleType;
        this.genre = genre;
        this.status = status;
        this.animation = animation;
        this.sort = sort;
        this.offset = offset;
        this.size = size;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTitleType() {
        return titleType;
    }

    public void setTitleType(String titleType) {
        this.titleType = titleType;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getAnimation() {
        return animation;
    }

    public void setAnimation(Boolean animation) {
        this.animation = animation;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

}
