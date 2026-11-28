package com.sleekydz86.contentmeta.global.procedure;

public record Worker(String name, String ip) {

    public static final Worker SYSTEM = new Worker("SYSTEM", "127.0.0.1");
    public static final Worker BATCH = new Worker("BATCH", "127.0.0.1");
}
