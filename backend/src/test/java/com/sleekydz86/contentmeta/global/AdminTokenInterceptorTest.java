package com.sleekydz86.contentmeta.global;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.global.security.AdminTokenInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminTokenInterceptorTest {

    @Test
    @DisplayName("설정한 토큰과 같을 때만 통과한다")
    void matchesOnlySameToken() {
        assertTrue(AdminTokenInterceptor.matches("secret", "secret"));
        assertFalse(AdminTokenInterceptor.matches("secret", "Secret"));
        assertFalse(AdminTokenInterceptor.matches("secret", null));
    }

    @Test
    @DisplayName("토큰을 설정하지 않았으면 무엇이 와도 거절한다")
    void rejectsWhenNotConfigured() {
        assertFalse(AdminTokenInterceptor.matches("", ""));
        assertFalse(AdminTokenInterceptor.matches("  ", "  "));
    }
}
