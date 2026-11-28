package com.sleekydz86.contentmeta.dashboard.presentation;

import com.sleekydz86.contentmeta.dashboard.application.CatalogStatistics;
import com.sleekydz86.contentmeta.dashboard.application.DashboardOverview;
import com.sleekydz86.contentmeta.dashboard.application.DashboardService;
import com.sleekydz86.contentmeta.dashboard.application.QualityDetail;
import com.sleekydz86.contentmeta.dashboard.application.VerifyDetail;
import com.sleekydz86.contentmeta.dashboard.infrastructure.BatchTrendRow;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public DashboardOverview overview() {
        return dashboardService.overview();
    }

    @GetMapping("/statistics")
    public CatalogStatistics statistics() {
        return dashboardService.statistics();
    }

    @GetMapping("/batch-trend")
    public List<BatchTrendRow> batchTrend(@RequestParam(defaultValue = "7") int days) {
        return dashboardService.batchTrend(days);
    }

    @GetMapping("/verify/latest")
    public ResponseEntity<VerifyDetail> latestVerify() {
        return dashboardService.latestVerify().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/quality/latest")
    public ResponseEntity<QualityDetail> latestQuality() {
        return dashboardService.latestQuality().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
