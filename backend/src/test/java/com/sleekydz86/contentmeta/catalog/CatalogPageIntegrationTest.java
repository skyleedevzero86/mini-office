package com.sleekydz86.contentmeta.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.catalog.application.CatalogQueryService;
import com.sleekydz86.contentmeta.catalog.application.TitleListItem;
import com.sleekydz86.contentmeta.catalog.application.TitleSort;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.global.web.PageResponse;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 목록 화면용 페이지 조회 (샘플 백필 뒤: 작품 11편 = 영화 5 + 시리즈 6, 그중 애니메이션 3) */
class CatalogPageIntegrationTest extends IntegrationTestSupport {

    @Autowired
    CatalogQueryService catalogQueryService;

    private PageResponse<TitleListItem> page(int page, int size, String query, String type, String genre, Boolean animation, TitleSort sort) {
        return catalogQueryService.page(query, "ko", type, genre, null, animation, sort, page, size);
    }

    private PageResponse<TitleListItem> all(String query, String type, String genre, Boolean animation) {
        return page(0, 20, query, type, genre, animation, TitleSort.RELEASE_DESC);
    }

    @Test
    @DisplayName("읽기 원천이 LEGACY면 목록도 열리지 않는다")
    void closedWhileLegacy() {
        backfillAll();
        BusinessException blocked = assertThrows(BusinessException.class, () -> all(null, null, null, null));
        assertEquals(ErrorCode.READ_SOURCE_LEGACY, blocked.getErrorCode());
    }

    @Test
    @DisplayName("전체 건수와 페이지 수를 주고, 마지막 페이지는 남은 만큼만 준다")
    void pagingTotals() {
        openNewReadSource();

        PageResponse<TitleListItem> first = page(0, 4, null, null, null, null, TitleSort.RELEASE_DESC);
        assertEquals(11, first.total());
        assertEquals(3, first.totalPages());
        assertEquals(4, first.items().size());
        assertEquals("Broken Range", first.items().getFirst().originalTitle());   // 공개일 최신순

        PageResponse<TitleListItem> last = page(2, 4, null, null, null, null, TitleSort.RELEASE_DESC);
        assertEquals(3, last.items().size());
        assertEquals("Game of Thrones", last.items().getLast().originalTitle());

        PageResponse<TitleListItem> beyond = page(5, 4, null, null, null, null, TitleSort.RELEASE_DESC);
        assertEquals(11, beyond.total());
        assertTrue(beyond.items().isEmpty());
    }

    @Test
    @DisplayName("유형·애니메이션·장르·검색어 필터와 정렬을 건다")
    void filtersAndSort() {
        openNewReadSource();

        assertEquals(5, all(null, "movie", null, null).total());
        assertEquals(3, all(null, "SERIES", null, true).total());
        assertEquals(7, all(null, null, "drama", null).total());
        assertEquals(2, all("게임", null, null, null).total());   // 한국어 현지화 제목으로도 찾는다

        List<TitleListItem> byRating = page(0, 20, null, null, null, null, TitleSort.RATING_DESC).items();
        assertEquals("타오르는 여인의 초상", byRating.getFirst().originalTitle());
        byRating.subList(byRating.size() - 3, byRating.size()).forEach(item -> assertNull(item.voteAverage()));   // 평점 없는 작품은 뒤로

        TitleListItem got = all("Game of Thrones", null, null, null).items().getFirst();
        assertEquals(List.of("TMDB_TV"), got.providers());
        assertTrue(got.genres().contains("드라마"));
    }

    @Test
    @DisplayName("음수 페이지는 거절한다")
    void rejectsNegativePage() {
        openNewReadSource();
        BusinessException bad = assertThrows(BusinessException.class, () -> page(-1, 4, null, null, null, null, TitleSort.RELEASE_DESC));
        assertEquals(ErrorCode.INVALID_INPUT, bad.getErrorCode());
    }
}
