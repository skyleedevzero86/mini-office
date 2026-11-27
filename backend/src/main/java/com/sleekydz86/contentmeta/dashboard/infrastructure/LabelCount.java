package com.sleekydz86.contentmeta.dashboard.infrastructure;

/** 이름별 건수 (차트용) */
public class LabelCount {

    private String label = "";
    private int cnt;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getCnt() {
        return cnt;
    }

    public void setCnt(int cnt) {
        this.cnt = cnt;
    }

}
