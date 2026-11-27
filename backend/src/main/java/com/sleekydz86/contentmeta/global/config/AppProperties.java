package com.sleekydz86.contentmeta.global.config;

import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** application.yml의 app.* 설정 */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("") String adminToken,
        @DefaultValue("LEGACY") ReadSource readSource,
        @DefaultValue Batch batch,
        @DefaultValue Tmdb tmdb
) {

    /** 정기 배치 (기본 꺼짐) */
    public record Batch(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("60000") long syncDelayMs,
            @DefaultValue("500") int syncBatchSize,
            @DefaultValue("0 10 * * * *") String verifyCron,
            @DefaultValue("0 10 4 * * *") String qualityCron
    ) {
    }

    /** TMDB API. apiKey가 비어 있으면 TMDB 보강을 쓰지 않는다 (v3 키 또는 v4 읽기 토큰) */
    public record Tmdb(
            @DefaultValue("") String apiKey,
            @DefaultValue("https://api.themoviedb.org/3") String baseUrl,
            @DefaultValue("https://image.tmdb.org/t/p") String imageBaseUrl,
            @DefaultValue("ko-KR") String language,
            @DefaultValue("5000") int timeoutMs,
            @DefaultValue("30") int cacheMinutes
    ) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
