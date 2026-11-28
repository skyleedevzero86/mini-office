package com.sleekydz86.contentmeta.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.migration.domain.VerifyResult;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationRunRow;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BackfillLoadTest extends IntegrationTestSupport {

    private static final Logger log = LoggerFactory.getLogger(BackfillLoadTest.class);

    @Test
    @DisplayName("대량 백필 뒤 대조가 0-0-0이고 작품 수가 원본과 맞는다")
    void largeBackfillVerifiesClean() {
        jdbc.execute("CALL sp_generate_legacy_load(20000, 5000)");
        jdbc.execute("TRUNCATE TABLE legacy_change_log");
        int legacyRows = count("SELECT (SELECT COUNT(*) FROM movies) + (SELECT COUNT(*) FROM MergedTVShow) + (SELECT COUNT(*) FROM cartoon_video)");

        long started = System.currentTimeMillis();
        MigrationRunRow run = backfillAll(1000);
        long backfillMs = System.currentTimeMillis() - started;
        long verifyStarted = System.currentTimeMillis();
        VerifyResult verify = verifyNow();
        long verifyMs = System.currentTimeMillis() - verifyStarted;

        log.info("부하 백필: 원본 {}건, 작품 {}건, 출연 {}건, 묶음 {}개, {}ms ({}건/초), 대조 {}ms",
                legacyRows, run.getUpsertedTitles(), run.getUpsertedCredits(), run.getBatchCount(), backfillMs,
                run.getUpsertedTitles() * 1000L / Math.max(1, backfillMs), verifyMs);
        assertEquals(legacyRows - 5, run.getUpsertedTitles(), "샘플의 ERROR 5행만 빠진다");
        assertTrue(verify.clean());
    }
}
