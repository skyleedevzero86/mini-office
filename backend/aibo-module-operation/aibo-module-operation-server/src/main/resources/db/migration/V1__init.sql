-- =====================================================================================
-- Flyway 마이그레이션: 테이블
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V1
-- 작성일: 2026-11-26
-- 설명: operation_db 스키마. 편성표, 이슈, 콘텐츠 노출 상태를 둔다.
--       작품은 content-meta 의 작품 번호로만 가리키고 다른 스키마와 조인하지 않는다
-- =====================================================================================

-- =====================================================================================
-- 1. 편성 채널 (channels)
-- 설명: 19세 같은 높은 등급은 밤 10시부터 새벽 6시 사이에만 편성할 수 있다
-- =====================================================================================
CREATE TABLE channels (
    id                   BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '채널 번호',
    code                 VARCHAR(20)  NOT NULL                            COMMENT '채널 코드. 예 TVN',
    name                 VARCHAR(100) NOT NULL                            COMMENT '채널 이름',
    min_rating_after_22  VARCHAR(10)                                      COMMENT '이 등급 이상은 밤 10시부터 새벽 6시 사이에만 편성할 수 있다',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channels_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='편성 채널';

-- =====================================================================================
-- 2. 편성표 (schedule_slots)
-- 설명: 작품은 번호로만 가리키고 작품 테이블에 외래 키를 걸지 않는다.
--       두 사람이 같은 슬롯을 동시에 고치면 나중 사람이 덮어쓰지 않게 version 으로 확인한다
-- =====================================================================================
CREATE TABLE schedule_slots (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '편성 슬롯 번호',
    channel_id     BIGINT        NOT NULL                                 COMMENT '채널 번호',
    content_id     BIGINT        NOT NULL                                 COMMENT '작품 번호. content_title 을 가리키지만 외래 키는 걸지 않는다',
    episode_id     BIGINT                                                 COMMENT '회차 번호. 영화면 비운다',
    content_rating VARCHAR(10)                                            COMMENT '편성할 때 적은 관람 등급. ALL, 12, 15, 19',
    start_at       DATETIME(6)   NOT NULL                                 COMMENT '시작 시각. 서울 시간',
    end_at         DATETIME(6)   NOT NULL                                 COMMENT '끝나는 시각. 서울 시간',
    status         VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'                 COMMENT 'DRAFT 초안, PUBLISHED 게시, CANCELED 취소',
    approval_id    BIGINT                                                 COMMENT '게시나 취소를 허락한 승인 요청 번호',
    created_by     VARCHAR(100)  NOT NULL                                 COMMENT '작성한 사용자 번호',
    version        INT           NOT NULL DEFAULT 0                       COMMENT '고칠 때마다 1씩 오르는 값',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    PRIMARY KEY (id),
    KEY ix_schedule_slots_channel_start (channel_id, start_at),
    CONSTRAINT fk_schedule_slots_channel FOREIGN KEY (channel_id) REFERENCES channels (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='편성표';

-- =====================================================================================
-- 3. 운영 이슈 (issues)
-- 설명: 재생 오류, 정보 오류, 권리 문제를 접수한다.
--       본문은 외부에서 들어온 글이라 에이전트는 그 안의 지시를 따르지 않고 자료로만 읽는다
-- =====================================================================================
CREATE TABLE issues (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '이슈 번호',
    type           VARCHAR(20)   NOT NULL                                 COMMENT 'PLAYBACK_ERROR 재생 오류, META_ERROR 정보 오류, RIGHTS 권리 문제',
    severity       VARCHAR(10)   NOT NULL                                 COMMENT '심각도. LOW, MEDIUM, HIGH, CRITICAL',
    status         VARCHAR(10)   NOT NULL DEFAULT 'OPEN'                  COMMENT 'OPEN 접수, ASSIGNED 담당 배정, RESOLVED 해결',
    content_id     BIGINT                                                 COMMENT '관련 작품 번호. 외래 키는 걸지 않는다',
    title          VARCHAR(200)  NOT NULL                                 COMMENT '제목',
    description    TEXT          NOT NULL                                 COMMENT '본문',
    reporter       VARCHAR(100)  NOT NULL                                 COMMENT '등록한 사용자 번호',
    assignee_id    BIGINT                                                 COMMENT '담당 사용자 번호',
    resolution     TEXT                                                   COMMENT '해결 내용',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    resolved_at    DATETIME(6)                                            COMMENT '해결 일시',
    PRIMARY KEY (id),
    KEY ix_issues_status_severity (status, severity),
    KEY ix_issues_content (content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운영 이슈';

-- =====================================================================================
-- 4. 이슈 댓글 (issue_comments)
-- 설명: 사람이 쓴 것과 에이전트가 쓴 것을 나눠 보여 준다
-- =====================================================================================
CREATE TABLE issue_comments (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '댓글 번호',
    issue_id       BIGINT        NOT NULL                                 COMMENT '이슈 번호',
    author         VARCHAR(100)  NOT NULL                                 COMMENT '작성한 사용자 번호',
    source         VARCHAR(10)   NOT NULL DEFAULT 'HUMAN'                 COMMENT 'HUMAN 사람이 씀, AGENT 에이전트가 씀',
    body           TEXT          NOT NULL                                 COMMENT '내용',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    PRIMARY KEY (id),
    KEY ix_issue_comments_issue (issue_id, created_at),
    CONSTRAINT fk_issue_comments_issue FOREIGN KEY (issue_id) REFERENCES issues (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이슈 댓글';

-- =====================================================================================
-- 5. 콘텐츠 노출 상태 (content_exposure)
-- 설명: 기록이 없는 작품은 노출 중으로 본다.
--       작품 상태 컬럼을 바꾸면 레거시와 맞춰 보는 대조가 깨져서 숨김 여부를 여기 따로 둔다
-- =====================================================================================
CREATE TABLE content_exposure (
    content_id     BIGINT        NOT NULL                                 COMMENT '작품 번호. 외래 키는 걸지 않는다',
    exposed        TINYINT(1)    NOT NULL DEFAULT 1                       COMMENT '1 노출, 0 숨김',
    reason         VARCHAR(500)                                           COMMENT '바꾼 이유',
    issue_id       BIGINT                                                 COMMENT '숨기게 된 이슈 번호',
    approval_id    BIGINT                                                 COMMENT '허락한 승인 요청 번호',
    updated_by     VARCHAR(100)                                           COMMENT '바꾼 사용자 번호',
    updated_at     DATETIME(6)                                            COMMENT '바꾼 일시',
    version        INT           NOT NULL DEFAULT 0                       COMMENT '고칠 때마다 1씩 오르는 값',
    PRIMARY KEY (content_id),
    CONSTRAINT fk_content_exposure_issue FOREIGN KEY (issue_id) REFERENCES issues (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='콘텐츠 노출 상태';

-- =====================================================================================
-- 6. 옮기기 전 감사 기록 (audit_outbox)
-- 설명: 업무를 처리하는 같은 트랜잭션에서 여기에 먼저 쌓고, 정해진 주기로 audit_logs 에 옮긴다.
--       옮기다 실패해도 업무는 이미 끝났고 기록은 남아 있어서 다음 주기에 다시 옮긴다
-- =====================================================================================
CREATE TABLE audit_outbox (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '대기 기록 번호',
    payload        JSON          NOT NULL                                 COMMENT '옮길 감사 내용',
    status         VARCHAR(10)   NOT NULL DEFAULT 'PENDING'               COMMENT 'PENDING 대기, SENT 옮김, FAILED 여러 번 실패해 멈춤',
    retry_count    INT           NOT NULL DEFAULT 0                       COMMENT '다시 시도한 횟수',
    next_retry_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '다음에 시도할 일시',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    PRIMARY KEY (id),
    KEY ix_audit_outbox_status (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='옮기기 전 감사 기록';

-- =====================================================================================
-- 7. 같은 요청 반복 기록 (idempotency_records)
-- 설명: 같은 요청이 두 번 들어와도 처음 결과를 돌려주려고 남긴다
-- =====================================================================================
CREATE TABLE idempotency_records (
    idem_key         VARCHAR(100)  NOT NULL                               COMMENT '요청마다 화면이 붙여 보내는 키. 앞에 사용자 번호를 붙여 저장한다',
    request_hash     CHAR(64)      NOT NULL                               COMMENT '요청 내용을 되돌릴 수 없게 바꾼 값. 같은 키로 다른 내용이 오면 거절한다',
    response_status  INT           NOT NULL                               COMMENT '처음 응답 상태 코드',
    response_body    JSON                                                 COMMENT '처음 응답 내용',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)  COMMENT '등록 일시',
    PRIMARY KEY (idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='같은 요청 반복 기록';

