package com.sleekydz86.contentmeta.global.procedure;

/** 프로시저 작업 로그(work_log)에 남길 작업자와 IP */
public record Worker(String name, String ip) {

    public static final Worker SYSTEM = new Worker("SYSTEM", "127.0.0.1");
    public static final Worker BATCH = new Worker("BATCH", "127.0.0.1");
}
