package com.sleekydz86.contentmeta.operation.application;

import com.sleekydz86.contentmeta.operation.infrastructure.BatchRunFinishCall;
import com.sleekydz86.contentmeta.operation.infrastructure.BatchRunStartCall;
import com.sleekydz86.contentmeta.operation.infrastructure.OperationMapper;
import java.net.InetAddress;
import java.util.function.LongToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 배치 한 번을 batch_job_run에 시작·종료로 남기며 돌린다.
 * - 같은 작업이 이미 실행 중이면 시작하지 않는다 (서버가 여러 대여도 한 번만 돈다)
 * - 실패는 기록하고 예외를 밖으로 던지지 않는다. 다음 주기에 다시 돈다 (프로시저가 묶음 단위로 롤백·재개를 맡는다)
 * - 허용 시간을 넘기면 SLA 위반으로 남기고 경고 로그를 낸다 (알림 연동 지점)
 */
@Component
public class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    private final OperationMapper mapper;
    private final String workerName;

    public BatchRunner(OperationMapper mapper) {
        this.mapper = mapper;
        this.workerName = hostName();
    }

    /** work: 실행 ID를 받아 처리 건수를 돌려준다 */
    public BatchOutcome run(BatchJob job, LongToIntFunction work) {
        BatchRunStartCall start = new BatchRunStartCall(job.name(), workerName);
        mapper.callBatchRunStart(start);
        Long jobRunId = start.getJobRunId();
        if (start.getRtnCod() == null || start.getRtnCod() != 1 || jobRunId == null) {
            log.info("배치 {} 건너뜀: {}", job, start.getErrMsg());
            return new BatchOutcome(job, null, true, false, 0, false, start.getErrMsg());
        }

        int processed = 0;
        String error = null;
        try {
            processed = work.applyAsInt(jobRunId);
        } catch (RuntimeException exception) {
            error = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            log.error("배치 {} 실패 (jobRunId={})", job, jobRunId, exception);
        }

        BatchRunFinishCall finish = new BatchRunFinishCall(jobRunId, error == null ? 1 : 0, processed, error);
        mapper.callBatchRunFinish(finish);
        boolean slaBreached = finish.getSlaBreached() != null && finish.getSlaBreached() == 1;
        if (slaBreached) {
            log.warn("배치 {} SLA 위반: 허용 시간을 넘겼습니다 (jobRunId={})", job, jobRunId);
        }
        return new BatchOutcome(job, jobRunId, false, error == null, processed, slaBreached, error);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "unknown";
        }
    }
}
