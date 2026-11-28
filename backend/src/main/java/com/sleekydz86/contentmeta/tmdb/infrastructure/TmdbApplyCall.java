package com.sleekydz86.contentmeta.tmdb.infrastructure;

import com.sleekydz86.contentmeta.global.procedure.ProcedureCall;

public class TmdbApplyCall extends ProcedureCall {

    private String worker;
    private String workip;
    private long titleId;
    private String posterPath;
    private String imdbId;
    private String enTitle;
    private String enOverview;
    private String note;

    public TmdbApplyCall(String worker, String workip, long titleId, String posterPath, String imdbId, String enTitle, String enOverview) {
        super("sp_title_tmdb_apply");
        this.worker = worker;
        this.workip = workip;
        this.titleId = titleId;
        this.posterPath = posterPath;
        this.imdbId = imdbId;
        this.enTitle = enTitle;
        this.enOverview = enOverview;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public String getWorkip() {
        return workip;
    }

    public void setWorkip(String workip) {
        this.workip = workip;
    }

    public long getTitleId() {
        return titleId;
    }

    public void setTitleId(long titleId) {
        this.titleId = titleId;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public String getEnTitle() {
        return enTitle;
    }

    public void setEnTitle(String enTitle) {
        this.enTitle = enTitle;
    }

    public String getEnOverview() {
        return enOverview;
    }

    public void setEnOverview(String enOverview) {
        this.enOverview = enOverview;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

}
