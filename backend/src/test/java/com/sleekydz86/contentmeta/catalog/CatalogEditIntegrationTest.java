package com.sleekydz86.contentmeta.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.catalog.application.CatalogEditService;
import com.sleekydz86.contentmeta.catalog.application.ExternalIdCommand;
import com.sleekydz86.contentmeta.catalog.application.LocalizationCommand;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CatalogEditIntegrationTest extends IntegrationTestSupport {

    @Autowired
    CatalogEditService catalogEditService;

    @Test
    @DisplayName("IMDB ID를 연결하면 작업 로그가 남고, 같은 ID를 다른 작품에 붙이면 거절한다")
    void linkImdbAndRejectDuplicate() {
        backfillAll();
        List<Long> ids = movieTitleIds();
        long first = ids.get(0);
        long second = ids.get(1);

        catalogEditService.externalId(new ExternalIdCommand("C", "TITLE", first, "IMDB", "tt7984734"));

        assertEquals("MANUAL", jdbc.queryForObject(
                "SELECT match_method FROM content_external_id WHERE entity_id = ? AND provider = 'IMDB'", String.class, first));
        assertEquals(1, count("SELECT COUNT(*) FROM work_log WHERE procedure_nm = 'sp_external_id_cud'"));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> catalogEditService.externalId(new ExternalIdCommand("C", "TITLE", second, "IMDB", "tt7984734")));
        assertEquals(ErrorCode.PROCEDURE_FAILED, duplicate.getErrorCode());
        assertTrue(duplicate.getDetail().contains("이미 TITLE " + first));
    }

    @Test
    @DisplayName("형식이 틀린 외부 ID와 잘못된 작업 구분은 거절한다")
    void rejectBadFormatAndOperation() {
        backfillAll();
        long first = movieTitleIds().get(0);

        BusinessException badFormat = assertThrows(BusinessException.class,
                () -> catalogEditService.externalId(new ExternalIdCommand("C", "TITLE", first, "KOBIS", "123")));
        assertTrue(badFormat.getDetail().contains("KOBIS ID 형식"));

        BusinessException badOperation = assertThrows(BusinessException.class,
                () -> catalogEditService.externalId(new ExternalIdCommand("X", "TITLE", first, "IMDB", "tt0000001")));
        assertEquals(ErrorCode.INVALID_INPUT, badOperation.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM content_external_id WHERE provider IN ('KOBIS', 'IMDB')"));
    }

    @Test
    @DisplayName("인물에 외부 ID가 붙으면 이름만 있는 인물 표시가 꺼진다")
    void personExternalIdClearsNameOnly() {
        backfillAll();
        int personId = count("SELECT MIN(person_id) FROM content_person");

        catalogEditService.externalId(new ExternalIdCommand("C", "PERSON", personId, "TMDB_PERSON", "55934"));

        assertEquals(0, count("SELECT name_only FROM content_person WHERE person_id = ?", personId));
    }

    @Test
    @DisplayName("운영자가 고친 한국어 제목은 다음 레거시 동기화가 덮지 않고, 한국어 현지화는 지울 수 없다")
    void manualLocalizationSurvivesSync() {
        backfillAll();
        long titleId = count("SELECT title_id FROM content_title WHERE origin_system = 'LEGACY_MOVIES' AND origin_key = '1'");

        catalogEditService.localization(new LocalizationCommand("U", titleId, "ko", "조조 래빗 (운영자 확인)", null));
        jdbc.update("UPDATE movies SET movie_content = '레거시에서 줄거리 수정' WHERE movie_seq = 1");
        migrationService.syncAll(100);

        assertEquals("조조 래빗 (운영자 확인)", jdbc.queryForObject(
                "SELECT local_title FROM content_localization WHERE title_id = ? AND lang_code = 'ko'", String.class, titleId));

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> catalogEditService.localization(new LocalizationCommand("D", titleId, "ko", null, null)));
        assertTrue(blocked.getDetail().contains("한국어 현지화는 지울 수 없습니다"));
    }

    private List<Long> movieTitleIds() {
        return jdbc.queryForList("SELECT title_id FROM content_title WHERE title_type = 'MOVIE' ORDER BY title_id LIMIT 2", Long.class);
    }
}
