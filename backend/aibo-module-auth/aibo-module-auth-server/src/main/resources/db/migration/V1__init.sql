-- =====================================================================================
-- Flyway 마이그레이션: 테이블
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V1
-- 작성일: 2026-11-26
-- 설명: auth_db 스키마. 사용자, 팀, 역할, 권한, 담당 채널, 로그인 연장 토큰을 둔다.
--       감사 기록은 이 서비스의 audit_outbox 에 먼저 쌓고 governance 서비스로 보낸다
-- =====================================================================================

-- =====================================================================================
-- 1. 팀 (teams)
-- 설명: 백오피스 사용자가 속한 팀
-- =====================================================================================
CREATE TABLE teams (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '팀 번호',
    name           VARCHAR(100)  NOT NULL                                 COMMENT '팀 이름',
    PRIMARY KEY (id),
    UNIQUE KEY uk_teams_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='백오피스 팀';

-- =====================================================================================
-- 2. 사용자 (users)
-- 설명: 로그인하는 사람. 비밀번호는 되돌릴 수 없게 바꾼 값만 저장한다
-- =====================================================================================
CREATE TABLE users (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '사용자 번호',
    email          VARCHAR(200)  NOT NULL                                 COMMENT '로그인 이메일',
    name           VARCHAR(100)  NOT NULL                                 COMMENT '화면에 보이는 이름',
    password_hash  VARCHAR(100)  NOT NULL                                 COMMENT '비밀번호를 되돌릴 수 없게 바꾼 값. 원래 비밀번호는 저장하지 않는다',
    team_id        BIGINT                                                 COMMENT '소속 팀 번호',
    status         VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE'                COMMENT 'ACTIVE 사용 중, LOCKED 잠김',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)    COMMENT '등록 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='백오피스 사용자';

-- =====================================================================================
-- 3. 역할 (roles)
-- 설명: 조회, 편성, 이슈 대응, 승인, 관리자 같은 역할 6개
-- =====================================================================================
CREATE TABLE roles (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '역할 번호',
    code           VARCHAR(30)   NOT NULL                                 COMMENT '역할 코드. VIEWER, SCHEDULER 같은 값',
    name           VARCHAR(100)  NOT NULL                                 COMMENT '역할 이름',
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='역할';

-- =====================================================================================
-- 4. 권한 (permissions)
-- 설명: 영역과 행동을 묶은 권한 코드 11개. 화면 버튼과 툴 사용을 이 코드로 막는다
-- =====================================================================================
CREATE TABLE permissions (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '권한 번호',
    code           VARCHAR(50)   NOT NULL                                 COMMENT '권한 코드. 영역:행동 모양으로 쓴다. 예 schedule:publish',
    description    VARCHAR(200)  NOT NULL                                 COMMENT '권한 설명',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='권한 코드';

-- =====================================================================================
-- 5. 사용자 역할 (user_roles)
-- 설명: 한 사용자가 여러 역할을 받을 수 있다
-- =====================================================================================
CREATE TABLE user_roles (
    user_id        BIGINT        NOT NULL                                 COMMENT '사용자 번호',
    role_id        BIGINT        NOT NULL                                 COMMENT '역할 번호',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자가 받은 역할';

-- =====================================================================================
-- 6. 역할 권한 (role_permissions)
-- 설명: 역할마다 가진 권한
-- =====================================================================================
CREATE TABLE role_permissions (
    role_id        BIGINT        NOT NULL                                 COMMENT '역할 번호',
    permission_id  BIGINT        NOT NULL                                 COMMENT '권한 번호',
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='역할에 묶인 권한';

-- =====================================================================================
-- 7. 담당 채널 (user_scopes)
-- 설명: 편성은 담당 채널 안에서만 다룰 수 있다. 승인자도 같은 채널을 맡아야 승인할 수 있다
-- =====================================================================================
CREATE TABLE user_scopes (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '담당 범위 번호',
    user_id        BIGINT        NOT NULL                                 COMMENT '사용자 번호',
    scope_type     VARCHAR(20)   NOT NULL DEFAULT 'CHANNEL'               COMMENT '범위 종류. 지금은 CHANNEL 하나',
    scope_value    VARCHAR(50)   NOT NULL                                 COMMENT '담당 채널 코드',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_scopes (user_id, scope_type, scope_value),
    CONSTRAINT fk_user_scopes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자가 맡은 채널';

-- =====================================================================================
-- 8. 로그인 연장 토큰 (refresh_tokens)
-- 설명: 토큰 원문 대신 되돌릴 수 없게 바꾼 값만 저장한다. 데이터베이스가 새어도 토큰을 쓸 수 없게 하려는 것이다
-- =====================================================================================
CREATE TABLE refresh_tokens (
    id             BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '토큰 번호',
    user_id        BIGINT        NOT NULL                                 COMMENT '사용자 번호',
    token_hash     CHAR(64)      NOT NULL                                 COMMENT '토큰을 되돌릴 수 없게 바꾼 값',
    expires_at     DATETIME(6)   NOT NULL                                 COMMENT '만료 일시',
    revoked_at     DATETIME(6)                                            COMMENT '폐기 일시. 비어 있으면 아직 쓸 수 있다',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='로그인 연장 토큰';

-- =====================================================================================
-- 9. 옮기기 전 감사 기록 (audit_outbox)
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

