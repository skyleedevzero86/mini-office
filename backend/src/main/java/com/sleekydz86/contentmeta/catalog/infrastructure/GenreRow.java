package com.sleekydz86.contentmeta.catalog.infrastructure;

/** 장르 필터 항목 (작품 수 포함) */
public class GenreRow {

    private String genreCode = "";
    private String nameKo = "";
    private int titleCount;

    public String getGenreCode() {
        return genreCode;
    }

    public void setGenreCode(String genreCode) {
        this.genreCode = genreCode;
    }

    public String getNameKo() {
        return nameKo;
    }

    public void setNameKo(String nameKo) {
        this.nameKo = nameKo;
    }

    public int getTitleCount() {
        return titleCount;
    }

    public void setTitleCount(int titleCount) {
        this.titleCount = titleCount;
    }

}
