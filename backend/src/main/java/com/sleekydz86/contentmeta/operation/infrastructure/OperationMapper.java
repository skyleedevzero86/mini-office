package com.sleekydz86.contentmeta.operation.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 배치 실행 기록·SLA·데이터 품질 (mapper/operation/OperationMapper.xml) */
@Mapper
public interface OperationMapper {

    void callBatchRunStart(BatchRunStartCall call);

    void callBatchRunFinish(BatchRunFinishCall call);

    void callRunQualityChecks(QualityCheckCall call);

    List<QualityResultRow> findQualityResults(@Param("jobRunId") long jobRunId);

    List<BatchRunRow> findBatchRuns(@Param("jobName") String jobName, @Param("limit") int limit);

    List<SlaFreshnessRow> checkSlaFreshness();
}
