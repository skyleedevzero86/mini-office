package com.sleekydz86.contentmeta.global.procedure;

import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;

/** sp_*_cud 프로시저 공통 결과: rtn_cod(1 성공, 0 실패), err_msg(한국어 사유) */
public abstract class ProcedureCall {

    public static final String SUCCESS = "1";

    private final String procedureName;
    private String rtnCod = "0";
    private String errMsg = "";

    protected ProcedureCall(String procedureName) {
        this.procedureName = procedureName;
    }

    /** 실패면 예외를 던져 서비스 트랜잭션을 롤백시킨다 */
    public void verify() {
        if (!SUCCESS.equals(rtnCod)) {
            String message = errMsg == null || errMsg.isBlank() ? "rtn_cod=" + rtnCod : errMsg;
            throw new BusinessException(ErrorCode.PROCEDURE_FAILED, procedureName + ": " + message);
        }
    }

    public String getProcedureName() {
        return procedureName;
    }

    public String getRtnCod() {
        return rtnCod;
    }

    public void setRtnCod(String rtnCod) {
        this.rtnCod = rtnCod;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
}
