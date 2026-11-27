-- =====================================================================================
-- Flyway 마이그레이션: 테이블
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V1
-- 작성일: 2026-11-26
-- 설명: agent_db 스키마. 에이전트 대화, 메시지, 툴 호출 기록, 시스템 프롬프트 버전을 둔다
-- =====================================================================================

-- =====================================================================================
-- 1. 시스템 프롬프트 (prompt_templates)
-- 설명: 에이전트에게 주는 기본 지시. 버전을 남겨 어떤 지시로 답했는지 나중에 확인한다
-- =====================================================================================
CREATE TABLE prompt_templates (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '프롬프트 번호',
    name           VARCHAR(50)   NOT NULL                                 COMMENT '이름',
    version        INT           NOT NULL                                 COMMENT '버전',
    system_prompt  TEXT          NOT NULL                                 COMMENT '에이전트에게 주는 기본 지시',
    active         TINYINT(1)    NOT NULL DEFAULT 0                       COMMENT '1 지금 쓰는 것',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_templates (name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시스템 프롬프트 버전';

-- =====================================================================================
-- 2. 에이전트 대화 (conversations)
-- 설명: 사용자별 대화 묶음
-- =====================================================================================
CREATE TABLE conversations (
    id                  VARCHAR(36)   NOT NULL                            COMMENT '대화 번호. 무작위로 만든 36자 값',
    user_id             VARCHAR(100)  NOT NULL                            COMMENT '사용자 번호',
    title               VARCHAR(200)  NOT NULL                            COMMENT '제목',
    prompt_template_id  BIGINT                                            COMMENT '이 대화에서 쓴 프롬프트 번호',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '시작 일시',
    PRIMARY KEY (id),
    KEY ix_conversations_user (user_id, created_at),
    CONSTRAINT fk_conversations_prompt FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='에이전트 대화';

-- =====================================================================================
-- 3. 대화 메시지 (messages)
-- 설명: 사용자 질문, 에이전트 답, 툴 결과와 사용한 토큰 수
-- =====================================================================================
CREATE TABLE messages (
    id               BIGINT        NOT NULL AUTO_INCREMENT                COMMENT '메시지 번호',
    conversation_id  VARCHAR(36)   NOT NULL                               COMMENT '대화 번호',
    role             VARCHAR(10)   NOT NULL                               COMMENT 'USER 사용자, ASSISTANT 에이전트, TOOL 툴 결과',
    content          TEXT          NOT NULL                               COMMENT '내용',
    input_tokens     INT                                                  COMMENT '모델에 넣은 토큰 수',
    output_tokens    INT                                                  COMMENT '모델이 낸 토큰 수',
    model            VARCHAR(100)                                         COMMENT '답한 모델 이름',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)  COMMENT '등록 일시',
    PRIMARY KEY (id),
    KEY ix_messages_conversation (conversation_id, id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='대화 메시지';

-- =====================================================================================
-- 4. 에이전트 툴 호출 기록 (tool_invocations)
-- 설명: 에이전트가 어떤 툴을 어떤 값으로 불렀고 결과가 어땠는지 남긴다
-- =====================================================================================
CREATE TABLE tool_invocations (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '툴 호출 번호',
    message_id     BIGINT        NOT NULL                                 COMMENT '이 호출을 만든 에이전트 메시지 번호',
    tool_name      VARCHAR(60)   NOT NULL                                 COMMENT '툴 이름',
    arguments      JSON                                                   COMMENT '툴에 넘긴 값',
    status         VARCHAR(20)   NOT NULL                                 COMMENT 'OK 성공, DENIED 거절, PENDING_APPROVAL 승인 대기, ERROR 오류',
    approval_id    BIGINT                                                 COMMENT '승인 요청 번호',
    latency_ms     INT           NOT NULL DEFAULT 0                       COMMENT '걸린 시간. 밀리초 단위',
    error_code     VARCHAR(30)                                            COMMENT '오류 코드',
    trace_id       VARCHAR(64)                                            COMMENT '요청 추적 번호',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    PRIMARY KEY (id),
    KEY ix_tool_invocations_message (message_id),
    CONSTRAINT fk_tool_invocations_message FOREIGN KEY (message_id) REFERENCES messages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='에이전트 툴 호출 기록';

