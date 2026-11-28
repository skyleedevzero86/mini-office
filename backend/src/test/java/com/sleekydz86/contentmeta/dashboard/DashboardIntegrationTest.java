package com.sleekydz86.contentmeta.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sleekydz86.contentmeta.dashboard.application.CatalogStatistics;
import com.sleekydz86.contentmeta.dashboard.application.DashboardOverview;
import com.sleekydz86.contentmeta.dashboard.application.DashboardService;
import com.sleekydz86.contentmeta.dashboard.application.QualityDetail;
import com.sleekydz86.contentmeta.dashboard.application.VerifyDetail;
import com.sleekydz86.contentmeta.dashboard.infrastructure.BatchTrendRow;
import com.sleekydz86.contentmeta.dashboard.infrastructure.LabelCount;
import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import com.sleekydz86.contentmeta.operation.application.OperationService;
import com.sleekydz86.contentmeta.support.IntegrationTestSupport;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DashboardIntegrationTest extends IntegrationTestSupport {

    @Autowired
    DashboardService dashboardService;

    @Autowired
    OperationService operationService;

    @Test
    @DisplayName("대조 전에는 읽기 전환 불가로 보이고, 0-0-0 대조 뒤에는 가능으로 보인다")
    void overviewShowsReadSwitchReadiness() {
        backfillAll();

        DashboardOverview before = dashboardService.overview();
        assertEquals(ReadSource.LEGACY, before.readSource());
        assertEquals(null, before.latestVerify());
        assertFalse(before.readSwitchAllowed());
        assertEquals(11, before.counts().getTotalTitles());
        assertEquals(5, before.counts().getMovieCount());
        assertEquals(6, before.counts().getSeriesCount());
        assertEquals(3, before.counts().getAnimationCount());
        assertEquals(0, before.counts().getPendingChanges());

        verifyNow();
        DashboardOverview after = dashboardService.overview();
        assertTrue(after.readSwitchAllowed());
        assertEquals(0, after.latestVerify().getMissing());
        VerifyDetail detail = dashboardService.latestVerify().orElseThrow();
        assertFalse(detail.tables().isEmpty());
        assertTrue(detail.tables().stream().allMatch(row -> row.getMissingCount() + row.getMismatchCount() + row.getOrphanCount() == 0));
    }

    @Test
    @DisplayName("레거시가 바뀌면 동기화 대기 건수가 보인다")
    void pendingChangesAreCounted() {
        backfillAll();
        jdbc.update("UPDATE movies SET movie_title = '조조 래빗 (재개봉)' WHERE movie_seq = 1");

        DashboardOverview overview = dashboardService.overview();
        assertEquals(1, overview.counts().getPendingChanges());
        assertNotNull(overview.counts().getOldestPendingChangeAt());
    }

    @Test
    @DisplayName("통계는 유형·장르·제공처를 세고, 연결이 없는 제공처도 0으로 보여 준다")
    void statisticsCounts() {
        backfillAll();

        CatalogStatistics stats = dashboardService.statistics();
        Map<String, Integer> byType = stats.byType().stream().collect(Collectors.toMap(LabelCount::getLabel, LabelCount::getCnt));
        assertEquals(Map.of("MOVIE", 5, "SERIES", 3, "SERIES_ANIMATION", 3), byType);
        assertEquals("드라마", stats.byGenre().getFirst().getLabel());
        assertEquals(7, stats.byGenre().getFirst().getCnt());
        Map<String, Integer> byProvider = stats.byProvider().stream().collect(Collectors.toMap(LabelCount::getLabel, LabelCount::getCnt));
        assertEquals(5, byProvider.size());
        assertEquals(3, (int) byProvider.get("TMDB_TV"));
        assertEquals(0, (int) byProvider.get("KOBIS"));
        assertFalse(stats.issuesByType().isEmpty());
    }

    @Test
    @DisplayName("품질 점검을 돌리면 최근 결과와 배치 추이에 남는다")
    void qualityRunAppearsInDashboard() {
        backfillAll();
        assertTrue(dashboardService.latestQuality().isEmpty());

        operationService.qualityCheck();

        QualityDetail quality = dashboardService.latestQuality().orElseThrow();
        assertEquals(count("SELECT COUNT(*) FROM dq_rule WHERE enabled = 1"), quality.rules().size());
        BatchTrendRow trend = dashboardService.batchTrend(7).stream()
                .filter(row -> "QUALITY_CHECK".equals(row.getJobName())).findFirst().orElseThrow();
        assertEquals(1, trend.getSuccessCount() + trend.getFailedCount());
        assertEquals(quality.rules().size(), dashboardService.overview().latestQuality().totalRules());
    }
}
