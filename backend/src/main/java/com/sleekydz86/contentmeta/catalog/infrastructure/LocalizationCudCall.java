package com.sleekydz86.contentmeta.catalog.infrastructure;

import com.sleekydz86.contentmeta.global.procedure.ProcedureCall;

public class LocalizationCudCall extends ProcedureCall {

    private String operation;
    private String worker;
    private String workip;
    private long titleId;
    private String langCode;
    private String localTitle;
    private String overview;

    public LocalizationCudCall(String operation, String worker, String workip, long titleId, String langCode, String localTitle, String overview) {
        super("sp_localization_cud");
        this.operation = operation;
        this.worker = worker;
        this.workip = workip;
        this.titleId = titleId;
        this.langCode = langCode;
        this.localTitle = localTitle;
        this.overview = overview;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
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

    public String getLangCode() {
        return langCode;
    }

    public void setLangCode(String langCode) {
        this.langCode = langCode;
    }

    public String getLocalTitle() {
        return localTitle;
    }

    public void setLocalTitle(String localTitle) {
        this.localTitle = localTitle;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

}
