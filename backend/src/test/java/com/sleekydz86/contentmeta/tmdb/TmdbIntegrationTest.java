package com.sleekydz86.contentmeta.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import com.sleekydz86.contentmeta.tmdb.application.TmdbService;
import com.sleekydz86.contentmeta.tmdb.application.TmdbViews;
import com.sleekydz86.contentmeta.tmdb.infrastructure.TmdbClient;
import com.sleekydz86.contentmeta.tmdb.infrastructure.TmdbModels;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class TmdbIntegrationTest extends IntegrationTestSupport {

    private static final long GOT = 1399L;

    @MockitoBean
    TmdbClient tmdbClient;

    @Autowired
    TmdbService tmdbService;

    @BeforeEach
    void clearCache() {
        tmdbService.clearCache();
    }

    private long gotTitleId() {
        return count("SELECT entity_id FROM content_external_id WHERE provider = 'TMDB_TV' AND external_id = '1399'");
    }

    private static TmdbModels.Tv got(String name, String overview, String posterPath) {
        TmdbModels.Credits credits = new TmdbModels.Credits(
                List.of(new TmdbModels.Cast(239019L, "Kit Harington", "Jon Snow", "/kit.jpg", 0)),
                List.of(new TmdbModels.Crew(9813L, "David Nutter", "Director", "Directing")));
        return new TmdbModels.Tv(GOT, name, "Game of Thrones", overview, null, posterPath, "/backdrop.jpg", "2011-04-17",
                List.of(60), 8, 73, 8.5, 25000, List.of(new TmdbModels.Genre(18, "드라마")), null, "Ended",
                List.of(new TmdbModels.Named(9813L, "David Benioff")), List.of(new TmdbModels.Named(49L, "HBO")),
                new TmdbModels.ExternalIds("tt0944947"), credits);
    }

    @Test
    @DisplayName("키가 없으면 상세 패널은 안내 상태로 오고, 반영은 거절한다")
    void notConfigured() {
        openNewReadSource();
        when(tmdbClient.configured()).thenReturn(false);
        when(tmdbClient.tv(eq(GOT), anyString())).thenThrow(new BusinessException(ErrorCode.TMDB_NOT_CONFIGURED));

        TmdbViews.Title view = tmdbService.titleView(gotTitleId());
        assertFalse(view.configured());

        BusinessException error = assertThrows(BusinessException.class, () -> tmdbService.apply(gotTitleId()));
        assertEquals(ErrorCode.TMDB_NOT_CONFIGURED, error.getErrorCode());
    }

    @Test
    @DisplayName("TMDB ID가 없는 작품은 linked=false로 온다")
    void notLinked() {
        openNewReadSource();
        when(tmdbClient.configured()).thenReturn(true);
        long movieId = count("SELECT MIN(title_id) FROM content_title WHERE title_type = 'MOVIE'");

        TmdbViews.Title view = tmdbService.titleView(movieId);

        assertTrue(view.configured());
        assertFalse(view.linked());
    }

    @Test
    @DisplayName("연결된 TV는 한국어 상세를 보여 주고, 같은 작품은 캐시에서 다시 쓴다")
    void viewLinkedTvWithCache() {
        openNewReadSource();
        when(tmdbClient.configured()).thenReturn(true);
        when(tmdbClient.tv(GOT, "ko-KR")).thenReturn(got("왕좌의 게임", "칠왕국의 철왕좌를 두고 벌어지는 이야기", "/ko-poster.jpg"));
        when(tmdbClient.imageUrl("/ko-poster.jpg", "w500")).thenReturn("https://image.tmdb.org/t/p/w500/ko-poster.jpg");

        TmdbViews.Title view = tmdbService.titleView(gotTitleId());
        tmdbService.titleView(gotTitleId());

        assertTrue(view.linked());
        assertEquals("왕좌의 게임", view.title());
        assertEquals("https://image.tmdb.org/t/p/w500/ko-poster.jpg", view.posterUrl());
        assertEquals(List.of("David Benioff"), view.directors());
        assertEquals("Jon Snow", view.cast().getFirst().character());
        assertEquals("tt0944947", view.imdbId());
        assertEquals(73, (int) view.episodes());
        verify(tmdbClient, times(1)).tv(GOT, "ko-KR");
    }

    @Test
    @DisplayName("반영하면 포스터·IMDB·영어 줄거리가 들어가고, 레거시 동기화와 대조가 그 값을 지우거나 불일치로 보지 않는다")
    void applyKeepsVerifyClean() {
        backfillAll();
        verifyNow();
        when(tmdbClient.configured()).thenReturn(true);
        when(tmdbClient.tv(GOT, "ko-KR")).thenReturn(got("왕좌의 게임", "한국어 줄거리", "/ko-poster.jpg"));
        when(tmdbClient.tv(GOT, "en-US")).thenReturn(got("Game of Thrones", "Seven noble families fight for control.", "/en-poster.jpg"));
        long titleId = gotTitleId();

        TmdbViews.ApplyResult result = tmdbService.apply(titleId);

        assertTrue(result.note().contains("포스터 반영"));
        assertTrue(result.note().contains("IMDB tt0944947 연결"));
        assertEquals("/ko-poster.jpg", jdbc.queryForObject("SELECT poster_path FROM content_title WHERE title_id = ?", String.class, titleId));
        assertEquals("API", jdbc.queryForObject(
                "SELECT match_method FROM content_external_id WHERE entity_id = ? AND provider = 'IMDB'", String.class, titleId));
        assertEquals("Seven noble families fight for control.", jdbc.queryForObject(
                "SELECT overview FROM content_localization WHERE title_id = ? AND lang_code = 'en'", String.class, titleId));
        assertEquals(1, count("SELECT COUNT(*) FROM work_log WHERE procedure_nm = 'sp_title_tmdb_apply'"));

        jdbc.update("UPDATE MergedTVShow SET overview = '레거시 줄거리 수정' WHERE tvNumber = '1399' AND mgSeq = 2");
        migrationService.syncAll(100);
        assertEquals("/ko-poster.jpg", jdbc.queryForObject("SELECT poster_path FROM content_title WHERE title_id = ?", String.class, titleId));
        assertTrue(verifyNow().clean());
    }
}
