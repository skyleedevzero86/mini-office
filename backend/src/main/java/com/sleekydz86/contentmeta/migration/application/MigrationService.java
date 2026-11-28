package com.sleekydz86.contentmeta.migration.application;

import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.migration.domain.BackfillResult;
import com.sleekydz86.contentmeta.migration.domain.MigrationMode;
import com.sleekydz86.contentmeta.migration.domain.PrecheckResult;
import com.sleekydz86.contentmeta.migration.domain.SyncResult;
import com.sleekydz86.contentmeta.migration.domain.VerifyResult;
import com.sleekydz86.contentmeta.migration.infrastructure.BackfillChunkCall;
import com.sleekydz86.contentmeta.migration.infrastructure.IssueRow;
import com.sleekydz86.contentmeta.migration.infrastructure.IssueSummaryRow;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationMapper;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationRunRow;
import com.sleekydz86.contentmeta.migration.infrastructure.SyncCall;
import com.sleekydz86.contentmeta.migration.infrastructure.ValidateCall;
import com.sleekydz86.contentmeta.migration.infrastructure.VerifyCall;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final MigrationMapper mapper;

    public MigrationService(MigrationMapper mapper) {
        this.mapper = mapper;
    }

    public MigrationRunRow createRun(MigrationMode mode, String description, int batchSize) {
        if (batchSize < 1 || batchSize > 5000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "묶음 크기는 1~5000이어야 합니다: " + batchSize);
        }
        MigrationRunRow row = new MigrationRunRow(mode.name(), description == null || description.isBlank() ? mode.label() : description, batchSize);
        mapper.insertRun(row);
        return run(row.getRunId());
    }

    public MigrationRunRow run(long runId) {
        MigrationRunRow row = mapper.findRun(runId);
        if (row == null) {
            throw new BusinessException(ErrorCode.MIGRATION_RUN_NOT_FOUND, "run_id=" + runId);
        }
        return row;
    }

    public List<MigrationRunRow> runs(int limit) {
        return mapper.findRuns(clamp(limit, 1, 200));
    }

    public List<IssueRow> issues(long runId, String severity, int limit) {
        run(runId);
        return mapper.findIssues(runId, severity == null ? null : severity.toUpperCase(), clamp(limit, 1, 1000));
    }

    public List<IssueSummaryRow> issueSummary(long runId) {
        run(runId);
        return mapper.summarizeIssues(runId);
    }

    public PrecheckResult precheck(long runId) {
        requireMode(runId, MigrationMode.BACKFILL);
        ValidateCall call = new ValidateCall(runId);
        procedure("사전 검증", () -> mapper.callValidate(call));
        return new PrecheckResult(runId, orZero(call.getErrorCount()), orZero(call.getInfoCount()));
    }

    public BackfillResult backfill(long runId, int maxChunks, long pauseMs) {
        requireMode(runId, MigrationMode.BACKFILL);
        long started = System.currentTimeMillis();
        int chunks = 0;
        int titles = 0;
        int errors = 0;
        boolean done = false;
        while (chunks < maxChunks && !done) {
            BackfillChunkCall call = new BackfillChunkCall(runId);
            procedure("백필 묶음", () -> mapper.callBackfillChunk(call));
            done = orZero(call.getDone()) == 1;
            if (!done) {
                chunks++;
                titles += orZero(call.getTitles());
                errors += orZero(call.getErrors());
                pause(pauseMs);
            }
        }
        BackfillResult result = new BackfillResult(runId, chunks, titles, errors, done, System.currentTimeMillis() - started);
        log.info("백필: {}", result);
        return result;
    }

    public SyncResult sync(long runId, int batchSize) {
        requireMode(runId, MigrationMode.SYNC);
        SyncCall call = new SyncCall(runId, clamp(batchSize, 1, 5000));
        procedure("변경분 동기화", () -> mapper.callSync(call));
        return new SyncResult(runId, orZero(call.getChanges()), orZero(call.getTitles()), orZero(call.getDeleted()), orZero(call.getErrors()));
    }

    public int syncAll(int batchSize) {
        Long latest = mapper.findLatestSyncRunId();
        long runId = latest != null ? latest : createRun(MigrationMode.SYNC, "상시 동기화", batchSize).getRunId();
        int total = 0;
        SyncResult result;
        do {
            result = sync(runId, batchSize);
            total += result.changes();
        } while (result.changes() == batchSize);
        return total;
    }

    public VerifyResult verify(long runId) {
        requireMode(runId, MigrationMode.VERIFY);
        VerifyCall call = new VerifyCall(runId);
        procedure("원본·신규 대조", () -> mapper.callVerify(call));
        return VerifyResult.of(runId, orZero(call.getMissing()), orZero(call.getMismatch()), orZero(call.getOrphan()), mapper.findVerifyTables(runId));
    }

    private void requireMode(long runId, MigrationMode mode) {
        MigrationRunRow run = run(runId);
        if (!mode.name().equals(run.getMode())) {
            throw new BusinessException(ErrorCode.MIGRATION_MODE,
                    "run_id=" + runId + " 는 " + run.getMode() + " 작업입니다. " + mode.name() + " 작업이 필요합니다.");
        }
    }

    private void procedure(String step, Runnable block) {
        try {
            block.run();
        } catch (DataAccessException exception) {
            Throwable cause = exception.getMostSpecificCause();
            String reason = cause.getMessage() != null ? cause.getMessage() : exception.getMessage();
            throw new BusinessException(ErrorCode.MIGRATION_FAILED, step + ": " + reason, exception);
        }
    }

    private static void pause(long pauseMs) {
        if (pauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
