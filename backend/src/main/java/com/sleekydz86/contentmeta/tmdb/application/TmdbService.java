package com.sleekydz86.contentmeta.tmdb.application;

import com.sleekydz86.contentmeta.catalog.infrastructure.CatalogMapper;
import com.sleekydz86.contentmeta.catalog.infrastructure.ExternalIdRow;
import com.sleekydz86.contentmeta.catalog.infrastructure.TitleRow;
import com.sleekydz86.contentmeta.global.config.AppProperties;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.global.procedure.Worker;
import com.sleekydz86.contentmeta.global.procedure.WorkerContext;
import com.sleekydz86.contentmeta.migration.application.ReadSourceSwitch;
import com.sleekydz86.contentmeta.tmdb.infrastructure.TmdbApplyCall;
import com.sleekydz86.contentmeta.tmdb.infrastructure.TmdbClient;
import com.sleekydz86.contentmeta.tmdb.infrastructure.TmdbMapper;
import com.sleekydz86.contentmeta.tmdb.infrastructure.TmdbModels;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TMDB 보강.
 * - 조회: 작품에 연결된 TMDB_MOVIE·TMDB_TV ID로 포스터·줄거리·출연진을 실시간으로 가져와 보여 준다 (메모리 캐시)
 * - 검색: TMDB ID가 없는 작품의 연결 후보를 찾는다 (연결은 sp_external_id_cud로 운영자가 한다)
 * - 반영: 확인한 값 중 포스터·IMDB ID·영어 제목/줄거리를 sp_title_tmdb_apply로 신규 메타에 넣는다
 */
@Service
public class TmdbService {

    private static final String DETAIL_LANGUAGE_EN = "en-US";
    private static final int CAST_LIMIT = 12;
    private static final int CACHE_LIMIT = 500;

    private final TmdbClient client;
    private final TmdbMapper tmdbMapper;
    private final CatalogMapper catalogMapper;
    private final ReadSourceSwitch readSourceSwitch;
    private final AppProperties.Tmdb properties;
    private final TransactionTemplate transactionTemplate;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public TmdbService(TmdbClient client, TmdbMapper tmdbMapper, CatalogMapper catalogMapper, ReadSourceSwitch readSourceSwitch,
                       AppProperties appProperties, PlatformTransactionManager transactionManager) {
        this.client = client;
        this.tmdbMapper = tmdbMapper;
        this.catalogMapper = catalogMapper;
        this.readSourceSwitch = readSourceSwitch;
        this.properties = appProperties.tmdb();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 캐시 비우기 (테스트, 운영자가 TMDB 수정분을 바로 보고 싶을 때) */
    public void clearCache() {
        cache.clear();
    }

    public TmdbViews.Status status() {
        return new TmdbViews.Status(client.configured(), properties.language());
    }

    /** 작품 상세의 TMDB 패널 */
    public TmdbViews.Title titleView(long titleId) {
        readSourceSwitch.requireNew();
        requireTitle(titleId);
        if (!client.configured()) {
            return TmdbViews.Title.notConfigured();
        }
        Optional<ExternalIdRow> link = tmdbLink(titleId);
        if (link.isEmpty()) {
            return TmdbViews.Title.notLinked();
        }
        long tmdbId = Long.parseLong(link.get().getExternalId());
        return "TMDB_TV".equals(link.get().getProvider())
                ? toView(cached("tv:" + tmdbId + ":" + properties.language(), () -> client.tv(tmdbId, properties.language())))
                : toView(cached("movie:" + tmdbId + ":" + properties.language(), () -> client.movie(tmdbId, properties.language())));
    }

    /** 연결 후보 검색. type: MOVIE | SERIES */
    public List<TmdbViews.Candidate> search(String type, String query, Integer year) {
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "검색어가 필요합니다.");
        }
        boolean tv = "SERIES".equalsIgnoreCase(type);
        String provider = tv ? "TMDB_TV" : "TMDB_MOVIE";
        TmdbModels.SearchPage page = client.search(tv ? "tv" : "movie", query.trim(), year, properties.language());
        List<TmdbModels.SearchItem> results = page.results() == null ? List.of() : page.results();
        return results.stream().limit(20).map(item -> new TmdbViews.Candidate(
                provider,
                item.id(),
                tv ? item.name() : item.title(),
                tv ? item.originalName() : item.originalTitle(),
                tv ? item.firstAirDate() : item.releaseDate(),
                client.imageUrl(item.posterPath(), "w185"),
                item.overview(),
                item.voteAverage(),
                catalogMapper.findTitleIdByExternalId(provider, String.valueOf(item.id())))).toList();
    }

    /**
     * TMDB 값을 신규 메타에 반영한다 (포스터·IMDB·영어 현지화). 대조 지문 컬럼은 건드리지 않는다.
     * TMDB 호출은 트랜잭션 밖에서 하고, 프로시저 호출만 트랜잭션으로 묶는다 (외부 응답을 기다리며 잠금을 잡지 않게)
     */
    public TmdbViews.ApplyResult apply(long titleId) {
        requireTitle(titleId);
        ExternalIdRow link = tmdbLink(titleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TMDB_NOT_LINKED, "title_id=" + titleId));
        long tmdbId = Long.parseLong(link.getExternalId());

        String posterPath;
        String imdbId;
        String enTitle;
        String enOverview;
        if ("TMDB_TV".equals(link.getProvider())) {
            TmdbModels.Tv local = cached("tv:" + tmdbId + ":" + properties.language(), () -> client.tv(tmdbId, properties.language()));
            TmdbModels.Tv english = cached("tv:" + tmdbId + ":" + DETAIL_LANGUAGE_EN, () -> client.tv(tmdbId, DETAIL_LANGUAGE_EN));
            posterPath = firstNonBlank(local.posterPath(), english.posterPath());
            imdbId = english.externalIds() == null ? null : english.externalIds().imdbId();
            enTitle = english.name();
            enOverview = english.overview();
        } else {
            TmdbModels.Movie local = cached("movie:" + tmdbId + ":" + properties.language(), () -> client.movie(tmdbId, properties.language()));
            TmdbModels.Movie english = cached("movie:" + tmdbId + ":" + DETAIL_LANGUAGE_EN, () -> client.movie(tmdbId, DETAIL_LANGUAGE_EN));
            posterPath = firstNonBlank(local.posterPath(), english.posterPath());
            imdbId = english.imdbId();
            enTitle = english.title();
            enOverview = english.overview();
        }

        Worker worker = WorkerContext.current();
        TmdbApplyCall call = new TmdbApplyCall(worker.name(), worker.ip(), titleId, posterPath, blankToNull(imdbId), enTitle, enOverview);
        transactionTemplate.executeWithoutResult(status -> {
            tmdbMapper.callTitleTmdbApply(call);
            call.verify();
        });
        return new TmdbViews.ApplyResult(titleId, call.getNote(), posterPath, blankToNull(imdbId));
    }

    private void requireTitle(long titleId) {
        TitleRow title = catalogMapper.findTitle(titleId);
        if (title == null) {
            throw new BusinessException(ErrorCode.TITLE_NOT_FOUND, "title_id=" + titleId);
        }
    }

    private Optional<ExternalIdRow> tmdbLink(long titleId) {
        return catalogMapper.findExternalIds("TITLE", titleId).stream()
                .filter(row -> "TMDB_MOVIE".equals(row.getProvider()) || "TMDB_TV".equals(row.getProvider()))
                .findFirst();
    }

    private TmdbViews.Title toView(TmdbModels.Movie movie) {
        TmdbModels.Credits credits = movie.credits();
        return new TmdbViews.Title(true, true, "TMDB_MOVIE", movie.id(), movie.title(), movie.originalTitle(), movie.overview(),
                blankToNull(movie.tagline()), movie.posterPath(), client.imageUrl(movie.posterPath(), "w500"),
                client.imageUrl(movie.backdropPath(), "w1280"), blankToNull(movie.releaseDate()), movie.runtime(),
                movie.voteAverage(), movie.voteCount(), genreNames(movie.genres()), directors(credits), cast(credits),
                blankToNull(movie.imdbId()), blankToNull(movie.homepage()), "https://www.themoviedb.org/movie/" + movie.id(),
                movie.status(), null, null, List.of());
    }

    private TmdbViews.Title toView(TmdbModels.Tv tv) {
        TmdbModels.Credits credits = tv.credits();
        Integer runtime = tv.episodeRunTime() == null || tv.episodeRunTime().isEmpty() ? null : tv.episodeRunTime().getFirst();
        List<String> creators = tv.createdBy() == null ? List.of() : tv.createdBy().stream().map(TmdbModels.Named::name).toList();
        List<String> networks = tv.networks() == null ? List.of() : tv.networks().stream().map(TmdbModels.Named::name).toList();
        return new TmdbViews.Title(true, true, "TMDB_TV", tv.id(), tv.name(), tv.originalName(), tv.overview(),
                blankToNull(tv.tagline()), tv.posterPath(), client.imageUrl(tv.posterPath(), "w500"),
                client.imageUrl(tv.backdropPath(), "w1280"), blankToNull(tv.firstAirDate()), runtime,
                tv.voteAverage(), tv.voteCount(), genreNames(tv.genres()), creators.isEmpty() ? directors(credits) : creators,
                cast(credits), tv.externalIds() == null ? null : blankToNull(tv.externalIds().imdbId()), blankToNull(tv.homepage()),
                "https://www.themoviedb.org/tv/" + tv.id(), tv.status(), tv.numberOfSeasons(), tv.numberOfEpisodes(), networks);
    }

    private static List<String> genreNames(List<TmdbModels.Genre> genres) {
        return genres == null ? List.of() : genres.stream().map(TmdbModels.Genre::name).filter(Objects::nonNull).toList();
    }

    private static List<String> directors(TmdbModels.Credits credits) {
        if (credits == null || credits.crew() == null) {
            return List.of();
        }
        return credits.crew().stream().filter(crew -> "Director".equals(crew.job())).map(TmdbModels.Crew::name).distinct().toList();
    }

    private List<TmdbViews.Person> cast(TmdbModels.Credits credits) {
        if (credits == null || credits.cast() == null) {
            return List.of();
        }
        return credits.cast().stream().limit(CAST_LIMIT)
                .map(cast -> new TmdbViews.Person(cast.id(), cast.name(), cast.character(), client.imageUrl(cast.profilePath(), "w185")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> loader) {
        Instant now = Instant.now();
        Cached hit = cache.get(key);
        if (hit != null && hit.expiresAt().isAfter(now)) {
            return (T) hit.value();
        }
        T value = loader.get();
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, new Cached(value, now.plus(Duration.ofMinutes(properties.cacheMinutes()))));
        return value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : blankToNull(second);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Cached(Object value, Instant expiresAt) {
    }
}
