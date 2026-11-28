package com.sleekydz86.contentmeta.dashboard.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {

    OverviewRow findOverview();

    List<LabelCount> countByType();

    List<LabelCount> countByGenre(@Param("limit") int limit);

    List<LabelCount> countByReleaseYear();

    List<LabelCount> countByStatus();

    List<LabelCount> countByProvider();

    List<LabelCount> countByLanguage();

    List<LabelCount> countIssuesByType();

    List<BatchTrendRow> findBatchTrend(@Param("days") int days);

    Long findLatestQualityJobRunId();
}
