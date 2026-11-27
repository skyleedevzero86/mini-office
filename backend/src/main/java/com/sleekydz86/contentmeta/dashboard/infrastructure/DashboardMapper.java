package com.sleekydz86.contentmeta.dashboard.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 대시보드·통계 화면용 집계 조회. 데이터는 바꾸지 않는다 (mapper/dashboard/DashboardMapper.xml) */
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
