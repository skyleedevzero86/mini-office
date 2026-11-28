package com.sleekydz86.contentmeta.operation.application;

import com.sleekydz86.contentmeta.global.config.AppProperties;
import com.sleekydz86.contentmeta.migration.application.MigrationService;
import com.sleekydz86.contentmeta.migration.domain.MigrationMode;
import com.sleekydz86.contentmeta.migration.domain.VerifyResult;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationRunRow;
import com.sleekydz86.contentmeta.operation.infrastructure.BatchRunRow;
import com.sleekydz86.contentmeta.operation.infrastructure.OperationMapper;
import com.sleekydz86.contentmeta.operation.infrastructure.QualityCheckCall;
import com.sleekydz86.contentmeta.operation.infrastructure.QualityResultRow;
import com.sleekydz86.contentmeta.operation.infrastructure.SlaFreshnessRow;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OperationService {

    private static final Logger log = LoggerFactory.getLogger(OperationService.class);

    private final BatchRunner batchRunner;
    private final MigrationService migrationService;
    private final OperationMapper mapper;
    private final AppProperties properties;

    public OperationService(BatchRunner batchRunner, MigrationService migrationService, OperationMapper mapper, AppProperties properties) {
        this.batchRunner = batchRunner;
        this.migrationService = migrationService;
        this.mapper = mapper;
        this.properties = properties;
    }

    public BatchOutcome syncLegacyChanges() {
        return batchRunner.run(BatchJob.LEGACY_SYNC, jobRunId -> migrationService.syncAll(properties.batch().syncBatchSize()));
    }

    public BatchOutcome verify() {
        return batchRunner.run(BatchJob.LEGACY_VERIFY, jobRunId -> {
            MigrationRunRow run = migrationService.createRun(MigrationMode.VERIFY, "정기 대조 (batch " + jobRunId + ")", 500);
            VerifyResult result = migrationService.verify(run.getRunId());
            if (!result.clean()) {
                log.warn("정기 대조 불일치: 누락 {}, 불일치 {}, 고아 {} (run {})", result.missing(), result.mismatch(), result.orphan(), result.runId());
            }
            return result.missing() + result.mismatch() + result.orphan();
        });
    }

    public QualityReport qualityCheck() {
        AtomicInteger failed = new AtomicInteger();
        BatchOutcome outcome = batchRunner.run(BatchJob.QUALITY_CHECK, jobRunId -> {
            QualityCheckCall call = new QualityCheckCall(jobRunId);
            mapper.callRunQualityChecks(call);
            int failedRules = call.getFailedRules() == null ? 0 : call.getFailedRules();
            if (failedRules > 0) {
                log.warn("데이터 품질 FAIL 규칙 {}개 (jobRunId={})", failedRules, jobRunId);
            }
            failed.set(failedRules);
            return failedRules;
        });
        List<QualityResultRow> rules = outcome.jobRunId() == null ? List.of() : mapper.findQualityResults(outcome.jobRunId());
        return new QualityReport(outcome, failed.get(), rules);
    }

    public List<BatchRunRow> batchRuns(BatchJob job, int limit) {
        return mapper.findBatchRuns(job == null ? null : job.name(), Math.max(1, Math.min(200, limit)));
    }

    public List<SlaFreshnessRow> slaFreshness() {
        return mapper.checkSlaFreshness();
    }
}
