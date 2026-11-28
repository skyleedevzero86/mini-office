package com.sleekydz86.contentmeta.operation.infrastructure;

public class QualityResultRow {

    private String ruleCode = "";
    private String ruleName = "";
    private String severity = "";
    private int violationCount;
    private int threshold;
    private String result = "";
    private String sampleKeys;

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public int getViolationCount() {
        return violationCount;
    }

    public void setViolationCount(int violationCount) {
        this.violationCount = violationCount;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getSampleKeys() {
        return sampleKeys;
    }

    public void setSampleKeys(String sampleKeys) {
        this.sampleKeys = sampleKeys;
    }

}
