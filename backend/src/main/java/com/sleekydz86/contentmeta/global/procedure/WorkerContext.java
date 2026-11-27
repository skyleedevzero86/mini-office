package com.sleekydz86.contentmeta.global.procedure;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 요청 헤더(X-Worker, X-Forwarded-For)에서 작업자를 읽는다. 요청 밖(배치)이면 SYSTEM */
public final class WorkerContext {

    private WorkerContext() {
    }

    public static Worker current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Worker.SYSTEM;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String name = request.getHeader("X-Worker");
        name = name == null ? "" : decode(name).trim();
        if (name.isEmpty()) {
            name = "관리자";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String address = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        String ip = "0:0:0:0:0:0:0:1".equals(address) || "::1".equals(address) ? "127.0.0.1" : address;
        return new Worker(name, ip);
    }

    // 화면은 한글 이름을 encodeURIComponent로 보낸다. 형식이 깨졌으면 받은 그대로 쓴다
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }
}
