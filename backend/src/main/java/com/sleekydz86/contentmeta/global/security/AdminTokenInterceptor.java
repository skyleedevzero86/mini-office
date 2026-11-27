package com.sleekydz86.contentmeta.global.security;

import com.sleekydz86.contentmeta.global.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 운영 API(/api/admin/**)는 X-Admin-Token 헤더가 설정값과 같을 때만 들어온다.
 * 설정값이 비어 있으면 모두 거절한다 (토큰 없이 띄운 서버에서 이관·삭제가 열리지 않게).
 */
@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Token";

    private final AppProperties properties;

    public AdminTokenInterceptor(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // 브라우저 사전 요청(OPTIONS)은 토큰 없이 통과시킨다
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (matches(properties.adminToken(), request.getHeader(HEADER))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"C401\",\"message\":\"운영 API 토큰이 없거나 맞지 않습니다.\",\"detail\":null}");
        return false;
    }

    /** 길이가 같아도 시간 차로 토큰이 새지 않게 상수 시간 비교 */
    public static boolean matches(String expected, String given) {
        return expected != null && !expected.isBlank() && given != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
