package com.sleekydz86.contentmeta.migration.infrastructure;

/** 원본 테이블·유형·심각도별 문제 건수 */
public class IssueSummaryRow {

    private String sourceTable = "";
    private String issueType = "";
    private String severity = "";
    private int cnt;

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getCnt() {
        return cnt;
    }

    public void setCnt(int cnt) {
        this.cnt = cnt;
    }

}
