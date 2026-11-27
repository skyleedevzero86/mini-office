-- =====================================================================================
-- Flyway 마이그레이션: 테이블
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V5
-- 작성일: 2026-11-27
-- 설명: 이슈 첨부 파일 정보를 둔다. 파일 내용은 파일 저장소에 있다
-- =====================================================================================

-- =====================================================================================
-- 1. 이슈 첨부 (issue_attachments)
-- 설명: 파일 저장소 안의 위치와 파일 정보를 남긴다
-- =====================================================================================
CREATE TABLE issue_attachments (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '첨부 번호',
    issue_id       BIGINT        NOT NULL                                 COMMENT '이슈 번호',
    object_key     VARCHAR(300)  NOT NULL                                 COMMENT '파일 저장소 안의 위치',
    file_name      VARCHAR(200)  NOT NULL                                 COMMENT '파일 이름',
    content_type   VARCHAR(100)  NOT NULL                                 COMMENT '파일 종류',
    size_bytes     BIGINT        NOT NULL                                 COMMENT '파일 크기',
    uploaded_by    VARCHAR(100)  NOT NULL                                 COMMENT '올린 사용자 번호',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_attachments_key (object_key),
    KEY ix_issue_attachments_issue (issue_id, created_at),
    CONSTRAINT fk_issue_attachments_issue FOREIGN KEY (issue_id) REFERENCES issues (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='이슈 첨부';
