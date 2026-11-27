-- =====================================================================================
-- Flyway 마이그레이션: 테이블
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V1
-- 작성일: 2026-11-26
-- 설명: mcp_db 스키마. 툴 호출 감사 기록을 governance 서비스로 보내기 전에 쌓아 두는 audit_outbox 하나만 둔다
-- =====================================================================================

-- =====================================================================================
-- 1. 옮기기 전 감사 기록 (audit_outbox)
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

