package com.sleekydz86.contentmeta.migration.infrastructure;

import com.sleekydz86.contentmeta.migration.domain.VerifyTableRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 레거시 → 신규 이관 작업과 프로시저 호출 (mapper/migration/MigrationMapper.xml) */
@Mapper
public interface MigrationMapper {

    int insertRun(MigrationRunRow row);

    MigrationRunRow findRun(@Param("runId") long runId);

    List<MigrationRunRow> findRuns(@Param("limit") int limit);

    Long findLatestSyncRunId();

    List<IssueRow> findIssues(@Param("runId") long runId, @Param("severity") String severity, @Param("limit") int limit);

    List<IssueSummaryRow> summarizeIssues(@Param("runId") long runId);

    List<VerifyTableRow> findVerifyTables(@Param("runId") long runId);

    LatestVerifyRow findLatestVerify();

    void callValidate(ValidateCall call);

    void callBackfillChunk(BackfillChunkCall call);

    void callSync(SyncCall call);

    void callVerify(VerifyCall call);
}
