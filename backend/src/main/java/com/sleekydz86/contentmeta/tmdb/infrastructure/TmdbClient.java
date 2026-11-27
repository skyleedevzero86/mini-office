package com.sleekydz86.contentmeta.tmdb.infrastructure;

import com.sleekydz86.contentmeta.global.config.AppProperties;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.util.function.Function;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * TMDB v3 호출. 키는 두 형식을 받는다:
 * - v3 API 키(32자) → api_key 쿼리 파라미터
 * - v4 읽기 액세스 토큰(eyJ로 시작) → Authorization: Bearer 헤더
 */
@Component
public class TmdbClient {

    private final AppProperties.Tmdb properties;
    private final RestClient restClient;

    public TmdbClient(AppProperties appProperties) {
        this.properties = appProperties.tmdb();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory);
        if (isBearerToken()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.apiKey().trim());
        }
        this.restClient = builder.build();
    }

    public boolean configured() {
        return properties.configured();
    }

    public TmdbModels.Movie movie(long movieId, String language) {
        return get(uri -> withAuth(uri.path("/movie/{id}")
                .queryParam("language", language)
                .queryParam("append_to_response", "credits")).build(movieId), TmdbModels.Movie.class, "movie " + movieId);
    }

    public TmdbModels.Tv tv(long tvId, String language) {
        return get(uri -> withAuth(uri.path("/tv/{id}")
                .queryParam("language", language)
                .queryParam("append_to_response", "credits,external_ids")).build(tvId), TmdbModels.Tv.class, "tv " + tvId);
    }

    /** type: movie | tv. year가 있으면 개봉(첫 방영) 연도로 좁힌다 */
    public TmdbModels.SearchPage search(String type, String query, Integer year, String language) {
        String yearParam = "tv".equals(type) ? "first_air_date_year" : "year";
        return get(uri -> {
            UriBuilder builder = uri.path("/search/" + type)
                    .queryParam("query", query)
                    .queryParam("language", language)
                    .queryParam("include_adult", false);
            if (year != null) {
                builder.queryParam(yearParam, year);
            }
            return withAuth(builder).build();
        }, TmdbModels.SearchPage.class, "search " + type);
    }

    public String imageUrl(String path, String size) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return properties.imageBaseUrl() + "/" + size + path;
    }

    private <T> T get(Function<UriBuilder, URI> uri, Class<T> type, String what) {
        if (!configured()) {
            throw new BusinessException(ErrorCode.TMDB_NOT_CONFIGURED);
        }
        try {
            T body = restClient.get().uri(uri).retrieve().body(type);
            if (body == null) {
                throw new BusinessException(ErrorCode.TMDB_UNAVAILABLE, what + ": 빈 응답");
            }
            return body;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(ErrorCode.TMDB_UNAVAILABLE, what + ": TMDB에 없는 ID입니다.");
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new BusinessException(ErrorCode.TMDB_UNAVAILABLE, "TMDB가 키를 거절했습니다(401). TMDB_API_KEY를 확인하세요.");
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.TMDB_UNAVAILABLE, what + ": " + exception.getMessage(), exception);
        }
    }

    private UriBuilder withAuth(UriBuilder builder) {
        return isBearerToken() ? builder : builder.queryParam("api_key", properties.apiKey().trim());
    }

    private boolean isBearerToken() {
        return properties.configured() && properties.apiKey().trim().startsWith("eyJ");
    }
}
