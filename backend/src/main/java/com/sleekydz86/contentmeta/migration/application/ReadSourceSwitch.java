package com.sleekydz86.contentmeta.migration.application;

import com.sleekydz86.contentmeta.global.config.AppProperties;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import com.sleekydz86.contentmeta.migration.infrastructure.LatestVerifyRow;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class ReadSourceSwitch {

    public static final long MAX_VERIFY_AGE_MINUTES = 120L;

    private final MigrationMapper mapper;
    private final AtomicReference<ReadSource> source;

    public ReadSourceSwitch(AppProperties properties, MigrationMapper mapper) {
        this.mapper = mapper;
        this.source = new AtomicReference<>(properties.readSource());
    }

    public ReadSource current() {
        return source.get();
    }

    public void requireNew() {
        if (current() != ReadSource.NEW) {
            throw new BusinessException(ErrorCode.READ_SOURCE_LEGACY);
        }
    }

    public static boolean allows(LatestVerifyRow latest) {
        return latest != null && latest.getAgeMinutes() <= MAX_VERIFY_AGE_MINUTES && latest.isClean();
    }

    public ReadSource switchTo(ReadSource target) {
        if (target == ReadSource.NEW) {
            LatestVerifyRow latest = mapper.findLatestVerify();
            if (latest == null) {
                throw new BusinessException(ErrorCode.READ_SWITCH_BLOCKED, "대조 기록이 없습니다. 먼저 대조를 돌리세요.");
            }
            if (latest.getAgeMinutes() > MAX_VERIFY_AGE_MINUTES) {
                throw new BusinessException(ErrorCode.READ_SWITCH_BLOCKED,
                        "최근 대조(run " + latest.getRunId() + ")가 " + latest.getAgeMinutes() + "분 전입니다. 다시 대조하세요.");
            }
            if (!latest.isClean()) {
                throw new BusinessException(ErrorCode.READ_SWITCH_BLOCKED,
                        "최근 대조(run " + latest.getRunId() + ")에 누락 " + latest.getMissing() + ", 불일치 " + latest.getMismatch()
                                + ", 고아 " + latest.getOrphan() + "건이 있습니다.");
            }
        }
        source.set(target);
        return target;
    }
}
