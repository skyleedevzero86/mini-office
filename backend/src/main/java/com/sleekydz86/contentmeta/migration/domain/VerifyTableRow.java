package com.sleekydz86.contentmeta.migration.domain;

public class VerifyTableRow {

    private String sourceTable = "";
    private int sourceCount;
    private int targetCount;
    private int missingCount;
    private int mismatchCount;
    private int orphanCount;

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public void setSourceCount(int sourceCount) {
        this.sourceCount = sourceCount;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = targetCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(int missingCount) {
        this.missingCount = missingCount;
    }

    public int getMismatchCount() {
        return mismatchCount;
    }

    public void setMismatchCount(int mismatchCount) {
        this.mismatchCount = mismatchCount;
    }

    public int getOrphanCount() {
        return orphanCount;
    }

    public void setOrphanCount(int orphanCount) {
        this.orphanCount = orphanCount;
    }

}
