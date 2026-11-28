package com.sleekydz86.contentmeta.dashboard.application;

import com.sleekydz86.contentmeta.dashboard.infrastructure.LabelCount;
import java.util.List;

public record CatalogStatistics(
        List<LabelCount> byType,
        List<LabelCount> byGenre,
        List<LabelCount> byReleaseYear,
        List<LabelCount> byStatus,
        List<LabelCount> byProvider,
        List<LabelCount> byLanguage,
        List<LabelCount> issuesByType
) {
}
