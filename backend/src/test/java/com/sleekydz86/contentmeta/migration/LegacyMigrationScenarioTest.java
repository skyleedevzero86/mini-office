package com.sleekydz86.contentmeta.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.catalog.application.CatalogQueryService;
import com.sleekydz86.contentmeta.catalog.application.TitleDetail;
import com.sleekydz86.contentmeta.catalog.infrastructure.LocalizationRow;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.migration.domain.BackfillResult;
import com.sleekydz86.contentmeta.migration.domain.MigrationMode;
import com.sleekydz86.contentmeta.migration.domain.PrecheckResult;
import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import com.sleekydz86.contentmeta.migration.domain.VerifyResult;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationRunRow;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LegacyMigrationScenarioTest extends IntegrationTestSupport {

    @Autowired
    CatalogQueryService catalogQueryService;

    @Test
    @DisplayName("사전 검증은 샘플에 넣은 오류 5건을 ERROR로, 형식 안내 12건을 INFO로 잡는다")
    void precheckFindsSeededIssues() {
        PrecheckResult result = migrationService.precheck(newRun(MigrationMode.BACKFILL));

        assertEquals(5, result.errorCount());
        assertEquals(12, result.infoCount());
        Set<String> errors = migrationService.issues(result.runId(), "ERROR", 100).stream()
                .map(issue -> issue.getSourceTable() + ":" + issue.getSourceKey() + ":" + issue.getIssueType())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "movies:5:REQUIRED_MISSING", "movies:6:INVALID_FORMAT", "movies:7:DUPLICATE_SOURCE_KEY",
                "MergedTVShow:4:DUPLICATE_EXTERNAL_ID", "MergedTVShow:5:CODE_MAPPING_FAILED"), errors);
        assertEquals(0, count("SELECT COUNT(*) FROM content_title"), "사전 검증은 데이터를 바꾸지 않는다");
    }

    @Test
    @DisplayName("백필은 묶음 단위로 체크포인트를 옮기며 ERROR 행만 빼고 적재하고, 다시 돌려도 늘어나지 않는다")
    void backfillIsChunkedAndIdempotent() {
        MigrationRunRow run = backfillAll(3);

        assertEquals("COMPLETED_WITH_ISSUES", run.getStatus());
        assertEquals(3, run.getBatchCount());
        assertEquals(11, run.getUpsertedTitles());
        assertEquals(17, run.getUpsertedCredits());
        assertEquals(38, run.getUpsertedEpisodes());
        assertEquals(8, run.getLastMovieSeq());
        assertEquals(26, count("SELECT episode_count FROM content_season s JOIN content_title t USING (title_id) "
                + "WHERE t.origin_system = 'LEGACY_CARTOON' AND t.origin_key = '1'"));
        assertEquals("93405", jdbc.queryForObject(
                "SELECT e.external_id FROM content_external_id e JOIN content_title t ON t.title_id = e.entity_id "
                        + "WHERE t.origin_system = 'LEGACY_TV' AND t.origin_key = '1' AND e.provider = 'TMDB_TV'", String.class));
        assertTrue(verifyNow().clean());

        List<Integer> before = snapshot();
        backfillAll(500);
        assertEquals(before, snapshot(), "재실행해도 작품·출연·에피소드·인물 수와 meta_version 합이 그대로다");
    }

    @Test
    @DisplayName("레거시 변경은 트리거로 기록되고, 동기화 전 대조가 잡아내며, 동기화 뒤에만 읽기를 NEW로 바꿀 수 있다")
    void changeCaptureSyncAndReadSwitch() {
        backfillAll();

        jdbc.update("UPDATE movies SET movie_title = '조조 래빗 (재개봉)' WHERE movie_seq = 1");
        jdbc.update("INSERT INTO movies (movie_title, movie_type, movie_open_date, movie_runtime, movie_actor, movie_country, movie_director, movie_age) "
                + "VALUES ('헤어질 결심', '드라마, 미스터리', '2022-06-29', '138분', '박해일, 탕웨이', '한국', '박찬욱', '15세')");
        jdbc.update("DELETE FROM MergedTVShow WHERE mgSeq = 3");
        jdbc.update("UPDATE MergedTVShow SET vote_count = vote_count + 1 WHERE mgSeq = 1");
        jdbc.update("UPDATE cartoon_video SET episode_end = '24' WHERE ctSeq = 2");
        assertEquals(4, count("SELECT COUNT(*) FROM legacy_change_log"), "조회수만 바뀐 UPDATE는 기록하지 않는다");

        VerifyResult dirty = verifyNow();
        assertEquals(List.of(1, 1, 1), List.of(dirty.missing(), dirty.mismatch(), dirty.orphan()));
        BusinessException blocked = assertThrows(BusinessException.class, () -> readSourceSwitch.switchTo(ReadSource.NEW));
        assertEquals(ErrorCode.READ_SWITCH_BLOCKED, blocked.getErrorCode());

        assertEquals(4, migrationService.syncAll(100));
        assertEquals("DELETED", jdbc.queryForObject(
                "SELECT status FROM content_title WHERE origin_system = 'LEGACY_TV' AND origin_key = '3'", String.class));
        assertEquals(24, count("SELECT COUNT(*) FROM content_episode e JOIN content_season s USING (season_id) "
                + "JOIN content_title t USING (title_id) WHERE t.origin_system = 'LEGACY_CARTOON' AND t.origin_key = '2'"));

        assertTrue(verifyNow().clean());
        assertEquals(ReadSource.NEW, readSourceSwitch.switchTo(ReadSource.NEW));

        TitleDetail got = catalogQueryService.byExternalId("TMDB_TV", "1399");
        assertEquals("Game of Thrones", got.title().getOriginalTitle());
        assertEquals(Set.of("en", "ko"), got.localizations().stream().map(LocalizationRow::getLangCode).collect(Collectors.toSet()));
        assertEquals("왕좌의 게임", got.localizations().stream()
                .filter(row -> "ko".equals(row.getLangCode())).findFirst().orElseThrow().getLocalTitle());
    }

    @Test
    @DisplayName("묶음 중간에 실패하면 그 묶음만 롤백되고 FAILED가 남으며, 원인을 고치면 체크포인트에서 이어 간다")
    void failedChunkRollsBackAndResumes() {
        long runId = newRun(MigrationMode.BACKFILL);
        jdbc.execute("RENAME TABLE content_title_genre TO content_title_genre_x");
        try {
            BusinessException failure = assertThrows(BusinessException.class, () -> migrationService.backfill(runId, 10, 0));
            assertEquals(ErrorCode.MIGRATION_FAILED, failure.getErrorCode());
        } finally {
            jdbc.execute("RENAME TABLE content_title_genre_x TO content_title_genre");
        }
        MigrationRunRow failed = migrationService.run(runId);
        assertEquals("FAILED", failed.getStatus());
        assertEquals(1, failed.getAttemptCount());
        assertEquals(0, failed.getLastMovieSeq());
        assertEquals(0, count("SELECT COUNT(*) FROM content_title"), "실패한 묶음은 한 행도 남기지 않는다");

        BackfillResult result = migrationService.backfill(runId, 10, 0);
        assertTrue(result.done());
        MigrationRunRow resumed = migrationService.run(runId);
        assertEquals("COMPLETED_WITH_ISSUES", resumed.getStatus());
        assertEquals(2, resumed.getAttemptCount());
        assertEquals(11, count("SELECT COUNT(*) FROM content_title"));
    }

    @Test
    @DisplayName("같은 작업으로 사전 검증 뒤 백필해도 검증 건수를 두 번 세지 않는다")
    void precheckThenBackfillDoesNotDoubleCount() {
        long runId = newRun(MigrationMode.BACKFILL);
        migrationService.precheck(runId);
        migrationService.backfill(runId, 100, 0);

        MigrationRunRow run = migrationService.run(runId);
        assertEquals(5, run.getErrorCount());
        assertEquals(12, run.getInfoCount());
    }

    @Test
    @DisplayName("다른 모드의 작업으로 단계를 부르면 거절한다")
    void rejectsWrongMode() {
        long verifyRun = newRun(MigrationMode.VERIFY);
        BusinessException error = assertThrows(BusinessException.class, () -> migrationService.backfill(verifyRun, 1, 0));
        assertEquals(ErrorCode.MIGRATION_MODE, error.getErrorCode());
    }

    private List<Integer> snapshot() {
        return List.of(
                count("SELECT COUNT(*) FROM content_title"),
                count("SELECT COUNT(*) FROM content_credit"),
                count("SELECT COUNT(*) FROM content_episode"),
                count("SELECT COUNT(*) FROM content_person"),
                count("SELECT SUM(meta_version) FROM content_title"));
    }
}
