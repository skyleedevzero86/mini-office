package com.sleekydz86.contentmeta.operation.application;

import com.sleekydz86.contentmeta.global.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationScheduler {

    private final OperationService operationService;
    private final AppProperties properties;

    public OperationScheduler(OperationService operationService, AppProperties properties) {
        this.operationService = operationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.batch.sync-delay-ms:60000}")
    public void syncLegacyChanges() {
        if (properties.batch().enabled()) {
            operationService.syncLegacyChanges();
        }
    }

    @Scheduled(cron = "${app.batch.verify-cron:0 10 * * * *}")
    public void verify() {
        if (properties.batch().enabled()) {
            operationService.verify();
        }
    }

    @Scheduled(cron = "${app.batch.quality-cron:0 10 4 * * *}")
    public void qualityCheck() {
        if (properties.batch().enabled()) {
            operationService.qualityCheck();
        }
    }
}
