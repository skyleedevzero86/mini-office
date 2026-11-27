-- =====================================================================================
-- Flyway 마이그레이션: 테이블
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V1
-- 작성일: 2026-11-26
-- 설명: governance_db 스키마. 툴 정책, 승인 요청, 감사 기록을 둔다.
--       다른 서비스의 감사 기록은 각자 outbox 에서 이 서비스로 보내져 audit_logs 에 쌓인다
-- =====================================================================================

-- =====================================================================================
-- 1. 툴 정책 (tool_policies)
-- 설명: 툴마다 위험도와 필요한 권한을 둔다. 위험도가 가장 높은 툴은 승인을 거쳐야 실행된다
-- =====================================================================================
CREATE TABLE tool_policies (
    tool_name             VARCHAR(60)  NOT NULL                           COMMENT '툴 이름',
    domain                VARCHAR(20)  NOT NULL                           COMMENT '영역. content, schedule, issue, governance',
    risk_level            VARCHAR(12)  NOT NULL                           COMMENT 'READ 조회, WRITE_LOW 바로 실행, WRITE_HIGH 승인 뒤 실행',
    required_permission   VARCHAR(50)  NOT NULL                           COMMENT '이 툴을 쓰는 데 필요한 권한 코드',
    enabled               TINYINT(1)   NOT NULL DEFAULT 1                 COMMENT '1 사용, 0 끔',
    approval_ttl_minutes  INT          NOT NULL DEFAULT 0                 COMMENT '승인 요청을 기다려 주는 시간. 분 단위',
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '바꾼 일시',
    PRIMARY KEY (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='툴 정책';

-- =====================================================================================
-- 2. 승인 요청 (approval_requests)
-- 설명: 위험한 변경은 바로 실행하지 않고 여기 남긴다. 요청한 사람과 다른 사람이 허락하면
--       그때 권한과 충돌을 다시 확인하고 실행한다. 같은 요청이 두 번 실행되지 않게 idempotency_key 를 겹치지 않게 둔다
-- =====================================================================================
CREATE TABLE approval_requests (
    id                BIGINT        NOT NULL AUTO_INCREMENT               COMMENT '승인 요청 번호',
    tool_name         VARCHAR(60)   NOT NULL                              COMMENT '실행할 툴 이름',
    arguments         JSON          NOT NULL                              COMMENT '툴에 넘길 값',
    risk_level        VARCHAR(12)   NOT NULL                              COMMENT '요청할 때의 위험도',
    requester_id      VARCHAR(100)  NOT NULL                              COMMENT '요청한 사용자 번호',
    requester_name    VARCHAR(100)                                        COMMENT '요청한 사용자 이름',
    requester_type    VARCHAR(10)   NOT NULL                              COMMENT 'HUMAN 사람이 올림, AGENT 에이전트가 올림',
    conversation_id   VARCHAR(36)                                         COMMENT '에이전트 대화 번호',
    scope_channel     VARCHAR(50)                                         COMMENT '대상 채널. 승인자도 이 채널을 맡고 있어야 한다',
    reason            TEXT                                                COMMENT '요청한 이유',
    status            VARCHAR(10)   NOT NULL DEFAULT 'PENDING'            COMMENT 'PENDING 대기, APPROVED 승인, REJECTED 반려, EXPIRED 만료, EXECUTED 실행 완료, FAILED 실행 실패',
    approver_id       VARCHAR(100)                                        COMMENT '승인하거나 반려한 사용자 번호',
    decision_comment  TEXT                                                COMMENT '승인이나 반려 의견',
    decided_at        DATETIME(6)                                         COMMENT '결정 일시',
    expires_at        DATETIME(6)   NOT NULL                              COMMENT '이 시각이 지나면 만료된다',
    idempotency_key   VARCHAR(100)  NOT NULL                              COMMENT '한 번만 실행되게 하는 키',
    result            JSON                                                COMMENT '실행 결과',
    trace_id          VARCHAR(64)                                         COMMENT '요청 추적 번호',
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '요청 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_requests_idem (idempotency_key),
    KEY ix_approval_requests_status (status, created_at),
    KEY ix_approval_requests_requester (requester_id),
    CONSTRAINT fk_approval_requests_tool FOREIGN KEY (tool_name) REFERENCES tool_policies (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='승인 요청';

-- =====================================================================================
-- 3. 감사 기록 (audit_logs)
-- 설명: 사람과 에이전트가 한 일을 모두 남긴다. 추가만 하고 고치거나 지우지 않는다.
--       운영 데이터베이스에서는 앱 계정에 이 테이블의 수정과 삭제 권한을 주지 않는다
-- =====================================================================================
CREATE TABLE audit_logs (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '감사 기록 번호',
    trace_id       VARCHAR(64)                                            COMMENT '요청 추적 번호. 한 흐름을 묶어 볼 때 쓴다',
    actor_type     VARCHAR(10)   NOT NULL                                 COMMENT 'HUMAN 사람, AGENT 에이전트, SYSTEM 시스템',
    actor_id       VARCHAR(100)  NOT NULL                                 COMMENT '한 사람이나 시스템',
    on_behalf_of   VARCHAR(100)                                           COMMENT '누구 대신 했는지',
    service        VARCHAR(30)   NOT NULL                                 COMMENT '기록을 남긴 영역',
    action         VARCHAR(100)  NOT NULL                                 COMMENT '툴 이름이나 화면 기능 이름',
    resource_type  VARCHAR(50)                                            COMMENT '대상 종류',
    resource_id    VARCHAR(100)                                           COMMENT '대상 번호',
    before_value   JSON                                                   COMMENT '바뀌기 전 값',
    after_value    JSON                                                   COMMENT '바뀐 뒤 값',
    result         VARCHAR(10)   NOT NULL                                 COMMENT 'SUCCESS 성공, DENIED 거절, PENDING 승인 대기, FAILED 실패',
    reason         VARCHAR(500)                                           COMMENT '거절이나 실패 이유',
    approval_id    BIGINT                                                 COMMENT '관련 승인 요청 번호',
    created_at     DATETIME(6)   NOT NULL                                 COMMENT '일어난 일시',
    PRIMARY KEY (id),
    KEY ix_audit_logs_actor (actor_id, created_at),
    KEY ix_audit_logs_resource (resource_type, resource_id),
    KEY ix_audit_logs_action (action, created_at),
    KEY ix_audit_logs_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='감사 기록. 추가만 한다';

-- =====================================================================================
-- 4. 옮기기 전 감사 기록 (audit_outbox)
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
-- 5. 같은 요청 반복 기록 (idempotency_records)
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

