package com.sleekydz86.contentmeta.dashboard.application;

import com.sleekydz86.contentmeta.dashboard.infrastructure.OverviewRow;
import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import com.sleekydz86.contentmeta.migration.infrastructure.LatestVerifyRow;
import com.sleekydz86.contentmeta.operation.infrastructure.SlaFreshnessRow;
import java.util.List;

public record DashboardOverview(
        ReadSource readSource,
        OverviewRow counts,
        LatestVerifyRow latestVerify,
        boolean readSwitchAllowed,
        QualitySummary latestQuality,
        List<SlaFreshnessRow> sla
) {

    public record QualitySummary(long jobRunId, int failedRules, int totalRules) {
    }
}
