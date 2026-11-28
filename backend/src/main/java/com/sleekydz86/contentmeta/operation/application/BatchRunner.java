package com.sleekydz86.contentmeta.operation.application;

import com.sleekydz86.contentmeta.operation.infrastructure.BatchRunFinishCall;
import com.sleekydz86.contentmeta.operation.infrastructure.BatchRunStartCall;
import com.sleekydz86.contentmeta.operation.infrastructure.OperationMapper;
import java.net.InetAddress;
import java.util.function.LongToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BatchRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);

    private final OperationMapper mapper;
    private final String workerName;

    public BatchRunner(OperationMapper mapper) {
        this.mapper = mapper;
        this.workerName = hostName();
    }

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
