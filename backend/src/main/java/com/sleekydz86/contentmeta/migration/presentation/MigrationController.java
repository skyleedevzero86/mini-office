package com.sleekydz86.contentmeta.migration.presentation;

import com.sleekydz86.contentmeta.migration.application.MigrationService;
import com.sleekydz86.contentmeta.migration.application.ReadSourceSwitch;
import com.sleekydz86.contentmeta.migration.domain.BackfillResult;
import com.sleekydz86.contentmeta.migration.domain.MigrationMode;
import com.sleekydz86.contentmeta.migration.domain.PrecheckResult;
import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import com.sleekydz86.contentmeta.migration.domain.SyncResult;
import com.sleekydz86.contentmeta.migration.domain.VerifyResult;
import com.sleekydz86.contentmeta.migration.infrastructure.IssueRow;
import com.sleekydz86.contentmeta.migration.infrastructure.IssueSummaryRow;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationRunRow;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/migration")
public class MigrationController {

    private final MigrationService migrationService;
    private final ReadSourceSwitch readSourceSwitch;

    public MigrationController(MigrationService migrationService, ReadSourceSwitch readSourceSwitch) {
        this.migrationService = migrationService;
        this.readSourceSwitch = readSourceSwitch;
    }

    @PostMapping("/runs")
    public MigrationRunRow createRun(
            @RequestParam MigrationMode mode,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "500") int batchSize) {
        return migrationService.createRun(mode, description, batchSize);
    }

    @GetMapping("/runs")
    public List<MigrationRunRow> runs(@RequestParam(defaultValue = "20") int limit) {
        return migrationService.runs(limit);
    }

    @GetMapping("/runs/{runId}")
    public MigrationRunRow run(@PathVariable long runId) {
        return migrationService.run(runId);
    }

    @GetMapping("/runs/{runId}/issues")
    public List<IssueRow> issues(
            @PathVariable long runId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "200") int limit) {
        return migrationService.issues(runId, severity, limit);
    }

    @GetMapping("/runs/{runId}/issues/summary")
    public List<IssueSummaryRow> issueSummary(@PathVariable long runId) {
        return migrationService.issueSummary(runId);
    }

    @PostMapping("/runs/{runId}/precheck")
    public PrecheckResult precheck(@PathVariable long runId) {
        return migrationService.precheck(runId);
    }

    @PostMapping("/runs/{runId}/backfill")
    public BackfillResult backfill(
            @PathVariable long runId,
            @RequestParam(defaultValue = "20") int maxChunks,
            @RequestParam(defaultValue = "100") long pauseMs) {
        return migrationService.backfill(runId, Math.max(1, Math.min(1000, maxChunks)), Math.max(0, Math.min(10_000, pauseMs)));
    }

    @PostMapping("/runs/{runId}/sync")
    public SyncResult sync(@PathVariable long runId, @RequestParam(defaultValue = "500") int batchSize) {
        return migrationService.sync(runId, batchSize);
    }

    @PostMapping("/runs/{runId}/verify")
    public VerifyResult verify(@PathVariable long runId) {
        return migrationService.verify(runId);
    }

    @GetMapping("/read-source")
    public Map<String, ReadSource> readSource() {
        return Map.of("readSource", readSourceSwitch.current());
    }

    @PostMapping("/read-source")
    public Map<String, ReadSource> switchReadSource(@RequestParam ReadSource target) {
        return Map.of("readSource", readSourceSwitch.switchTo(target));
    }
}
