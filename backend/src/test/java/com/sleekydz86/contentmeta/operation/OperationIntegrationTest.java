package com.sleekydz86.contentmeta.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.operation.application.BatchJob;
import com.sleekydz86.contentmeta.operation.application.BatchOutcome;
import com.sleekydz86.contentmeta.operation.application.BatchRunner;
import com.sleekydz86.contentmeta.operation.application.OperationService;
import com.sleekydz86.contentmeta.operation.application.QualityReport;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OperationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    OperationService operationService;

    @Autowired
    BatchRunner batchRunner;

    @AfterEach
    void restoreSla() {
        jdbc.update("UPDATE batch_job SET max_minutes = 30 WHERE job_name = 'QUALITY_CHECK'");
    }

    @Test
    @DisplayName("깨끗하게 이관하면 HIGH 규칙은 모두 통과한다")
    void cleanMigrationPassesQuality() {
        backfillAll();

        QualityReport report = operationService.qualityCheck();

        assertTrue(report.outcome().success());
        assertEquals(0, report.failedRules());
        assertEquals(9, report.rules().size());
    }

    @Test
    @DisplayName("깨진 IMDB ID, 빠진 회차, 멈춘 동기화를 넣으면 DQ001·DQ006·DQ009가 FAIL이다")
    void brokenDataFailsRules() {
        backfillAll();
        int titleId = count("SELECT MIN(title_id) FROM content_title WHERE title_type = 'MOVIE'");
        jdbc.update("INSERT INTO content_external_id (entity_type, entity_id, provider, external_id, match_method) "
                + "VALUES ('TITLE', ?, 'IMDB', 'tt12', 'LEGACY')", titleId);
        jdbc.update("DELETE FROM content_episode WHERE episode_no = 5 AND season_id = (SELECT MIN(season_id) FROM content_season)");
        jdbc.update("INSERT INTO legacy_change_log (source_table, source_seq, operation, changed_at) "
                + "VALUES ('movies', 1, 'U', NOW(3) - INTERVAL 30 MINUTE)");

        QualityReport report = operationService.qualityCheck();

        assertEquals(3, report.failedRules());
        assertEquals(Set.of("DQ001", "DQ006", "DQ009"), report.rules().stream()
                .filter(rule -> "FAIL".equals(rule.getResult())).map(rule -> rule.getRuleCode()).collect(Collectors.toSet()));
        assertEquals("TITLE:" + titleId + "=tt12", report.rules().stream()
                .filter(rule -> "DQ001".equals(rule.getRuleCode())).findFirst().orElseThrow().getSampleKeys());
    }

    @Test
    @DisplayName("같은 배치가 실행 중이면 겹쳐 돌지 않는다")
    void overlappingRunIsSkipped() {
        jdbc.update("INSERT INTO batch_job_run (job_name, worker) VALUES ('QUALITY_CHECK', 'other-server')");

        BatchOutcome outcome = operationService.qualityCheck().outcome();

        assertTrue(outcome.skipped());
        assertEquals("같은 작업이 이미 실행 중입니다.", outcome.message());
    }

    @Test
    @DisplayName("허용 시간을 넘기면 SLA 위반으로, 예외가 나면 FAILED와 사유로 남는다")
    void slaBreachAndFailureAreRecorded() {
        jdbc.update("UPDATE batch_job SET max_minutes = 0 WHERE job_name = 'QUALITY_CHECK'");

        BatchOutcome slow = batchRunner.run(BatchJob.QUALITY_CHECK, jobRunId -> {
            sleep(1_100);
            return 1;
        });
        assertTrue(slow.success());
        assertTrue(slow.slaBreached());

        BatchOutcome broken = batchRunner.run(BatchJob.QUALITY_CHECK, jobRunId -> {
            throw new IllegalStateException("외부 API 응답 없음");
        });
        assertFalse(broken.success());
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM batch_job_run WHERE job_run_id = ?", String.class, broken.jobRunId()));
        assertEquals("외부 API 응답 없음", jdbc.queryForObject("SELECT error_message FROM batch_job_run WHERE job_run_id = ?", String.class, broken.jobRunId()));
    }

    @Test
    @DisplayName("동기화 배치는 쌓인 변경을 모두 반영하고 처리 건수를 남긴다")
    void syncBatchAppliesAllChanges() {
        backfillAll();
        jdbc.update("UPDATE movies SET movie_content = '줄거리 수정' WHERE movie_seq IN (1, 2, 3)");

        BatchOutcome outcome = operationService.syncLegacyChanges();

        assertTrue(outcome.success());
        assertEquals(3, outcome.processed());
        assertEquals(0, count("SELECT COUNT(*) FROM legacy_change_log WHERE applied_run_id IS NULL"));
        assertEquals("OK", operationService.slaFreshness().stream()
                .filter(row -> "LEGACY_SYNC".equals(row.getJobName())).findFirst().orElseThrow().getFreshness());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
