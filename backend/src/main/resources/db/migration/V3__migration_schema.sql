-- =====================================================================================
-- Flyway 마이그레이션: 레거시 → 신규 이관 관리 스키마
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V3
-- 작성일: 2026-11-22
-- 설명: 이관 작업, 검증 결과, 이번 묶음의 처리 대상 키, 레거시 변경분 기록, 대조 결과 테이블을 만든다.
--       무중단 전환은 확장(새 테이블) → 백필(묶음 단위 적재) → 변경분 동기화(트리거) → 대조 → 읽기 전환 → 축소 순서로 진행한다
-- =====================================================================================

-- =====================================================================================
-- 1. 이관 작업 (migration_run)
-- 설명: 백필·동기화 작업의 상태, 진행 위치(체크포인트), 처리 건수를 관리한다
-- =====================================================================================
CREATE TABLE migration_run (
    run_id            BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '이관 작업 ID (자동증가)',
    mode              VARCHAR(10)  NOT NULL                               COMMENT '이관 모드 (BACKFILL:기존 데이터 묶음 적재, SYNC:레거시 변경분 반영, VERIFY:원본·신규 대조)',
    status            VARCHAR(30)  NOT NULL DEFAULT 'CREATED'             COMMENT '작업 상태 (CREATED:대기, RUNNING:실행 중, COMPLETED:완료, COMPLETED_WITH_ISSUES:검증 오류 있음, FAILED:실패)',
    description       VARCHAR(200)                                        COMMENT '작업 설명',
    batch_size        INT          NOT NULL DEFAULT 500                   COMMENT '묶음 하나의 최대 원본 행 수',
    last_movie_seq    INT          NOT NULL DEFAULT 0                     COMMENT '체크포인트: 마지막으로 처리한 movies.movie_seq',
    last_tv_seq       INT          NOT NULL DEFAULT 0                     COMMENT '체크포인트: 마지막으로 처리한 MergedTVShow.mgSeq',
    last_cartoon_seq  INT          NOT NULL DEFAULT 0                     COMMENT '체크포인트: 마지막으로 처리한 cartoon_video.ctSeq',
    batch_count       INT          NOT NULL DEFAULT 0                     COMMENT '처리한 묶음 수',
    attempt_count     INT          NOT NULL DEFAULT 0                     COMMENT '실행 횟수 (재처리 포함)',
    error_count       INT          NOT NULL DEFAULT 0                     COMMENT '누적 검증 오류(ERROR) 건수',
    info_count        INT          NOT NULL DEFAULT 0                     COMMENT '누적 검증 안내(INFO) 건수',
    upserted_titles   INT          NOT NULL DEFAULT 0                     COMMENT '누적 반영한 작품 수',
    upserted_credits  INT          NOT NULL DEFAULT 0                     COMMENT '누적 반영한 출연 수',
    upserted_episodes INT          NOT NULL DEFAULT 0                     COMMENT '누적 반영한 에피소드 수',
    error_message     TEXT                                                COMMENT '마지막 실패 사유',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '작업 생성일시',
    started_at        DATETIME(3)                                         COMMENT '마지막 실행 시작일시',
    finished_at       DATETIME(3)                                         COMMENT '마지막 실행 종료일시',
    PRIMARY KEY (run_id),
    CONSTRAINT ck_migration_run_mode CHECK (mode IN ('BACKFILL', 'SYNC', 'VERIFY')),
    CONSTRAINT ck_migration_run_status CHECK (status IN ('CREATED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ISSUES', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='레거시 → 신규 이관 작업';

-- =====================================================================================
-- 2. 이관 검증 결과 (migration_issue)
-- 설명: ERROR 행은 적재하지 않고, INFO 행은 적재하면서 안내만 남긴다
-- =====================================================================================
CREATE TABLE migration_issue (
    issue_id          BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '검증 결과 ID (자동증가)',
    run_id            BIGINT       NOT NULL                               COMMENT '이관 작업 ID',
    source_table      VARCHAR(30)  NOT NULL                               COMMENT '원본 테이블 (movies, MergedTVShow, cartoon_video)',
    source_key        VARCHAR(100) NOT NULL                               COMMENT '원본 키 (레거시 순번)',
    issue_type        VARCHAR(40)  NOT NULL                               COMMENT '유형 (REQUIRED_MISSING, INVALID_FORMAT, DUPLICATE_SOURCE_KEY, DUPLICATE_EXTERNAL_ID, CODE_MAPPING_FAILED, EXTERNAL_ID_MISSING, PERSON_NAME_ONLY, EPISODE_RANGE_INVALID)',
    severity          VARCHAR(5)   NOT NULL                               COMMENT '심각도 (ERROR:적재 제외, INFO:적재하고 안내)',
    message           VARCHAR(500) NOT NULL                               COMMENT '사유 (한국어)',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '기록일시',
    PRIMARY KEY (issue_id),
    CONSTRAINT ck_migration_issue_severity CHECK (severity IN ('ERROR', 'INFO')),
    KEY ix_migration_issue_row (run_id, source_table, source_key, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이관 검증 결과';

-- =====================================================================================
-- 3. 처리 대상 키 (migration_work_key)
-- 설명: 이번 묶음(백필) 또는 이번 동기화에서 처리할 레거시 키. 검증·적재 프로시저가 같은 대상을 보게 한다
-- =====================================================================================
CREATE TABLE migration_work_key (
    run_id            BIGINT       NOT NULL                               COMMENT '이관 작업 ID',
    source_table      VARCHAR(30)  NOT NULL                               COMMENT '원본 테이블',
    source_seq        INT          NOT NULL                               COMMENT '원본 순번',
    PRIMARY KEY (run_id, source_table, source_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이번 묶음의 처리 대상 키';

-- =====================================================================================
-- 4. 레거시 변경분 기록 (legacy_change_log)
-- 설명: 백필이 도는 동안·전환 전까지 레거시 화면에서 생긴 등록·수정·삭제를 트리거가 남긴다 (V6)
-- =====================================================================================
CREATE TABLE legacy_change_log (
    change_id         BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '변경 ID (자동증가, 처리 순서)',
    source_table      VARCHAR(30)  NOT NULL                               COMMENT '원본 테이블',
    source_seq        INT          NOT NULL                               COMMENT '원본 순번',
    operation         CHAR(1)      NOT NULL                               COMMENT '작업 구분 (I:등록, U:수정, D:삭제)',
    changed_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '변경일시',
    applied_run_id    BIGINT                                              COMMENT '반영한 동기화 작업 ID (NULL:미반영)',
    applied_at        DATETIME(3)                                         COMMENT '반영일시',
    PRIMARY KEY (change_id),
    CONSTRAINT ck_legacy_change_operation CHECK (operation IN ('I', 'U', 'D')),
    KEY ix_legacy_change_pending (applied_run_id, change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='레거시 변경분 기록';

-- =====================================================================================
-- 5. 이관 대조 결과 (migration_verify)
-- 설명: 원본과 신규의 건수·내용 지문(CRC32)을 원본 테이블별로 비교한 결과. 읽기 전환은 불일치 0일 때만 한다
-- =====================================================================================
CREATE TABLE migration_verify (
    verify_id         BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '대조 ID (자동증가)',
    run_id            BIGINT       NOT NULL                               COMMENT '이관 작업 ID',
    source_table      VARCHAR(30)  NOT NULL                               COMMENT '원본 테이블',
    source_count      INT          NOT NULL                               COMMENT '원본 건수 (ERROR 행 제외)',
    target_count      INT          NOT NULL                               COMMENT '신규 건수 (DELETED 제외)',
    missing_count     INT          NOT NULL                               COMMENT '원본에는 있고 신규에는 없는 건수',
    mismatch_count    INT          NOT NULL                               COMMENT '양쪽에 다 있지만 내용 지문이 다른 건수',
    orphan_count      INT          NOT NULL                               COMMENT '원본은 없어졌는데 신규에 살아 있는 건수 (DELETED 처리 누락)',
    verified_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '대조일시',
    PRIMARY KEY (verify_id),
    KEY ix_migration_verify_run (run_id, source_table)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이관 대조 결과';
