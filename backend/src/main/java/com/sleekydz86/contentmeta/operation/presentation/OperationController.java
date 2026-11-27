package com.sleekydz86.contentmeta.operation.presentation;

import com.sleekydz86.contentmeta.operation.application.BatchJob;
import com.sleekydz86.contentmeta.operation.application.BatchOutcome;
import com.sleekydz86.contentmeta.operation.application.OperationService;
import com.sleekydz86.contentmeta.operation.application.QualityReport;
import com.sleekydz86.contentmeta.operation.infrastructure.BatchRunRow;
import com.sleekydz86.contentmeta.operation.infrastructure.SlaFreshnessRow;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 배치·SLA·품질 운영 API. 정기 배치를 손으로 한 번 돌릴 때도 쓴다 */
@RestController
@RequestMapping("/api/admin/operation")
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
    }

    @PostMapping("/sync")
    public BatchOutcome sync() {
        return operationService.syncLegacyChanges();
    }

    @PostMapping("/verify")
    public BatchOutcome verify() {
        return operationService.verify();
    }

    @PostMapping("/quality-checks")
    public QualityReport qualityChecks() {
        return operationService.qualityCheck();
    }

    @GetMapping("/batch-runs")
    public List<BatchRunRow> batchRuns(
            @RequestParam(required = false) BatchJob job,
            @RequestParam(defaultValue = "50") int limit) {
        return operationService.batchRuns(job, limit);
    }

    @GetMapping("/sla")
    public List<SlaFreshnessRow> sla() {
        return operationService.slaFreshness();
    }
}
