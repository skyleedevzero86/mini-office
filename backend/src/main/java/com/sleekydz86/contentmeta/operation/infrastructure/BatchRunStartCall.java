package com.sleekydz86.contentmeta.operation.infrastructure;

/** sp_batch_run_start 호출 (겹쳐 돌면 rtnCod=0) */
public class BatchRunStartCall {

    private String jobName;
    private String worker;
    private Long jobRunId;
    private Integer rtnCod = 0;
    private String errMsg;

    public BatchRunStartCall() {
    }

    public BatchRunStartCall(String jobName, String worker) {
        this.jobName = jobName;
        this.worker = worker;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public Long getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(Long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public Integer getRtnCod() {
        return rtnCod;
    }

    public void setRtnCod(Integer rtnCod) {
        this.rtnCod = rtnCod;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

}
