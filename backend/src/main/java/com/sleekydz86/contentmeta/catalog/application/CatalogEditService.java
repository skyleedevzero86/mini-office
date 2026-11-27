package com.sleekydz86.contentmeta.catalog.application;

import com.sleekydz86.contentmeta.catalog.infrastructure.CatalogMapper;
import com.sleekydz86.contentmeta.catalog.infrastructure.ExternalIdCudCall;
import com.sleekydz86.contentmeta.catalog.infrastructure.LocalizationCudCall;
import com.sleekydz86.contentmeta.global.error.BusinessException;
import com.sleekydz86.contentmeta.global.error.ErrorCode;
import com.sleekydz86.contentmeta.global.procedure.Worker;
import com.sleekydz86.contentmeta.global.procedure.WorkerContext;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 외부 표준 ID 연결·현지화 편집. 커밋·롤백은 이 서비스 트랜잭션이 맡는다.
 * 프로시저가 rtn_cod=0을 돌려주면 verify()가 예외를 던져 롤백된다.
 */
@Service
public class CatalogEditService {

    private static final Set<String> OPERATIONS = Set.of("C", "U", "D");

    private final CatalogMapper mapper;

    public CatalogEditService(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void externalId(ExternalIdCommand command) {
        Worker worker = WorkerContext.current();
        ExternalIdCudCall call = new ExternalIdCudCall(
                operation(command.operation()),
                worker.name(),
                worker.ip(),
                required(command.entityType(), "entityType").toUpperCase(Locale.ROOT),
                command.entityId(),
                required(command.provider(), "provider").toUpperCase(Locale.ROOT),
                command.externalId() == null ? null : command.externalId().trim());
        mapper.externalIdCud(call);
        call.verify();
    }

    @Transactional
    public void localization(LocalizationCommand command) {
        Worker worker = WorkerContext.current();
        LocalizationCudCall call = new LocalizationCudCall(
                operation(command.operation()),
                worker.name(),
                worker.ip(),
                command.titleId(),
                required(command.langCode(), "langCode").trim(),
                command.localTitle(),
                command.overview());
        mapper.localizationCud(call);
        call.verify();
    }

    // 프로시저 파라미터가 CHAR(1)이라 길이가 다른 값은 프로시저에 닿기 전에 막는다
    private static String operation(String value) {
        String operation = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!OPERATIONS.contains(operation)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "작업 구분은 C, U, D 중 하나여야 합니다: " + value);
        }
        return operation;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, name + " 값이 필요합니다.");
        }
        return value;
    }
}
