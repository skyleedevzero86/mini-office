package com.sleekydz86.contentmeta.dashboard.application;

import com.sleekydz86.contentmeta.dashboard.infrastructure.BatchTrendRow;
import com.sleekydz86.contentmeta.dashboard.infrastructure.DashboardMapper;
import com.sleekydz86.contentmeta.migration.application.ReadSourceSwitch;
import com.sleekydz86.contentmeta.migration.infrastructure.LatestVerifyRow;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationMapper;
import com.sleekydz86.contentmeta.operation.infrastructure.OperationMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final int GENRE_LIMIT = 20;

    private final DashboardMapper mapper;
    private final MigrationMapper migrationMapper;
    private final OperationMapper operationMapper;
    private final ReadSourceSwitch readSourceSwitch;

    public DashboardService(DashboardMapper mapper, MigrationMapper migrationMapper, OperationMapper operationMapper,
                            ReadSourceSwitch readSourceSwitch) {
        this.mapper = mapper;
        this.migrationMapper = migrationMapper;
        this.operationMapper = operationMapper;
        this.readSourceSwitch = readSourceSwitch;
    }

    public DashboardOverview overview() {
        LatestVerifyRow latestVerify = migrationMapper.findLatestVerify();
        DashboardOverview.QualitySummary quality = latestQuality()
                .map(detail -> new DashboardOverview.QualitySummary(detail.jobRunId(),
                        (int) detail.rules().stream().filter(rule -> "FAIL".equals(rule.getResult())).count(),
                        detail.rules().size()))
                .orElse(null);
        return new DashboardOverview(
                readSourceSwitch.current(),
                mapper.findOverview(),
                latestVerify,
                ReadSourceSwitch.allows(latestVerify),
                quality,
                operationMapper.checkSlaFreshness());
    }

    public CatalogStatistics statistics() {
        return new CatalogStatistics(
                mapper.countByType(),
                mapper.countByGenre(GENRE_LIMIT),
                mapper.countByReleaseYear(),
                mapper.countByStatus(),
                mapper.countByProvider(),
                mapper.countByLanguage(),
                mapper.countIssuesByType());
    }

    public List<BatchTrendRow> batchTrend(int days) {
        return mapper.findBatchTrend(Math.max(1, Math.min(90, days)));
    }

    public Optional<VerifyDetail> latestVerify() {
        return Optional.ofNullable(migrationMapper.findLatestVerify())
                .map(latest -> new VerifyDetail(latest, migrationMapper.findVerifyTables(latest.getRunId())));
    }

    public Optional<QualityDetail> latestQuality() {
        return Optional.ofNullable(mapper.findLatestQualityJobRunId())
                .map(jobRunId -> new QualityDetail(jobRunId, operationMapper.findQualityResults(jobRunId)));
    }
}
