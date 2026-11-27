-- =====================================================================================
-- Flyway 마이그레이션: 등록·수정·삭제 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V9
-- 작성일: 2026-11-22
-- 설명: 운영자 화면의 외부 식별자 연결·현지화 편집을 sp_*_cud 프로시저로 처리한다.
--       모든 프로시저는 작업 구분, 작업자, 작업 IP를 받고 rtn_cod(1:성공, 0:실패)와 err_msg(한국어)를 돌려준다.
--       커밋·롤백은 호출한 서비스 트랜잭션이 맡는다
-- =====================================================================================

-- =====================================================================================
-- 1. 작업 로그 (work_log)
-- 설명: 프로시저가 처리한 등록·수정·삭제를 누가, 어디서, 언제 했는지 남기는 테이블
-- =====================================================================================
CREATE TABLE work_log (
    log_id         BIGINT        NOT NULL AUTO_INCREMENT                  COMMENT '작업 로그 ID (자동증가)',
    procedure_nm   VARCHAR(50)   NOT NULL                                 COMMENT '실행한 프로시저명',
    operation      CHAR(1)       NOT NULL                                 COMMENT '작업 구분 (C:등록, U:수정, D:삭제)',
    target_table   VARCHAR(50)   NOT NULL                                 COMMENT '대상 테이블명',
    target_key     VARCHAR(200)  NOT NULL                                 COMMENT '대상 키',
    detail         VARCHAR(1000)                                          COMMENT '작업 내용',
    worker         VARCHAR(100)  NOT NULL                                 COMMENT '작업자 (화면 사용자, BATCH)',
    work_ip        VARCHAR(50)   NOT NULL                                 COMMENT '작업 IP',
    work_dt        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)    COMMENT '작업일시',
    PRIMARY KEY (log_id),
    KEY ix_work_log_target (target_table, target_key, work_dt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='프로시저 등록·수정·삭제 작업 로그 (누가·어디서·언제·무엇을)';

DELIMITER $$

-- =====================================================================================
-- 2. 작업 로그 기록 (sp_write_work_log)
-- 설명: 각 sp_*_cud 프로시저가 처리 끝에 호출한다
-- =====================================================================================
CREATE PROCEDURE sp_write_work_log(
    IN in_procedure VARCHAR(50),                                      -- 프로시저명
    IN in_operation CHAR(1),                                          -- 작업 구분
    IN in_table     VARCHAR(50),                                      -- 대상 테이블
    IN in_key       VARCHAR(200),                                     -- 대상 키
    IN in_detail    VARCHAR(2000),                                    -- 작업 내용
    IN in_worker    VARCHAR(100),                                     -- 작업자
    IN in_workip    VARCHAR(50)                                       -- 작업 IP
)
BEGIN
    INSERT INTO work_log (procedure_nm, operation, target_table, target_key, detail, worker, work_ip)
    VALUES (in_procedure, in_operation, in_table, in_key, LEFT(in_detail, 1000),
            COALESCE(NULLIF(TRIM(in_worker), ''), 'anonymous'), COALESCE(NULLIF(TRIM(in_workip), ''), 'unknown'));
END$$

-- =====================================================================================
-- 3. 외부 식별자 형식 검사 (fn_external_id_format_ok)
-- 설명: 제공처·대상 유형별 ID 형식. DQ001·DQ002 품질 규칙과 같은 기준이다
-- =====================================================================================
CREATE FUNCTION fn_external_id_format_ok(
    p_provider    VARCHAR(20),                                        -- 제공처
    p_entity_type VARCHAR(10),                                        -- 대상 유형
    p_value       VARCHAR(50)                                         -- 외부 ID
) RETURNS TINYINT
DETERMINISTIC
NO SQL
BEGIN
    RETURN CASE
        WHEN p_provider = 'IMDB' AND p_entity_type = 'PERSON' THEN p_value REGEXP '^nm[0-9]{7,}$'
        WHEN p_provider = 'IMDB' THEN p_value REGEXP '^tt[0-9]{7,}$'
        WHEN p_provider = 'KOBIS' THEN p_entity_type = 'TITLE' AND p_value REGEXP '^[0-9]{8}$'
        WHEN p_provider = 'TMDB_PERSON' THEN p_entity_type = 'PERSON' AND p_value REGEXP '^[0-9]+$'
        WHEN p_provider IN ('TMDB_MOVIE', 'TMDB_TV') THEN p_entity_type IN ('TITLE', 'SEASON', 'EPISODE') AND p_value REGEXP '^[0-9]+$'
        ELSE 0
    END;
END$$

-- =====================================================================================
-- 4. 외부 식별자 등록·수정·삭제 (sp_external_id_cud)
-- 설명: C는 연결, U는 같은 대상·제공처의 ID를 바꾼다, D는 연결을 끊는다.
--       운영자가 직접 처리한 연결은 match_method=MANUAL, verified=1로 남는다.
--       인물에 외부 ID가 붙으면 이름만 있는 인물 표시(name_only)를 끈다
-- =====================================================================================
CREATE PROCEDURE sp_external_id_cud(
    IN    in_operation   CHAR(1),                                     -- 작업 구분 (C, U, D)
    IN    in_worker      VARCHAR(100),                                -- 작업자
    IN    in_workip      VARCHAR(50),                                 -- 작업 IP
    IN    in_entity_type VARCHAR(10),                                 -- 대상 유형 (TITLE, SEASON, EPISODE, PERSON)
    IN    in_entity_id   BIGINT,                                      -- 대상 ID
    IN    in_provider    VARCHAR(20),                                 -- 제공처
    IN    in_external_id VARCHAR(50),                                 -- 외부 ID (D는 비워도 된다)
    INOUT rtn_cod        VARCHAR(1),                                  -- 반환: 1 성공, 0 실패
    INOUT err_msg        VARCHAR(1000)                                -- 반환: 실패 사유 (한국어)
)
proc: BEGIN
    DECLARE v_exists  INT;
    DECLARE v_owner   VARCHAR(100);
    DECLARE v_value   VARCHAR(50) DEFAULT TRIM(in_external_id);
    DECLARE v_key     VARCHAR(200) DEFAULT CONCAT(in_entity_type, ':', in_entity_id, ':', in_provider);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 err_msg = MESSAGE_TEXT;
        SET rtn_cod = '0', err_msg = LEFT(CONCAT('DB 오류: ', err_msg), 1000);
    END;

    SET rtn_cod = '0', err_msg = '';

    -- 공통 입력값 확인
    IF NULLIF(TRIM(in_operation), '') IS NULL THEN SET err_msg = '작업 구분(in_operation)이 비어 있습니다.'; LEAVE proc; END IF;
    IF in_entity_type NOT IN ('TITLE', 'SEASON', 'EPISODE', 'PERSON') THEN SET err_msg = CONCAT('대상 유형이 잘못되었습니다. (', COALESCE(in_entity_type, '-'), ')'); LEAVE proc; END IF;
    IF in_provider NOT IN ('TMDB_MOVIE', 'TMDB_TV', 'TMDB_PERSON', 'IMDB', 'KOBIS') THEN SET err_msg = CONCAT('제공처가 잘못되었습니다. (', COALESCE(in_provider, '-'), ')'); LEAVE proc; END IF;

    -- 대상 존재 확인
    SET v_exists = CASE in_entity_type
        WHEN 'TITLE'   THEN (SELECT COUNT(*) FROM content_title   WHERE title_id = in_entity_id AND status <> 'DELETED')
        WHEN 'SEASON'  THEN (SELECT COUNT(*) FROM content_season  WHERE season_id = in_entity_id)
        WHEN 'EPISODE' THEN (SELECT COUNT(*) FROM content_episode WHERE episode_id = in_entity_id)
        ELSE                (SELECT COUNT(*) FROM content_person  WHERE person_id = in_entity_id) END;
    IF v_exists = 0 THEN SET err_msg = CONCAT('대상이 없거나 삭제되었습니다. (', in_entity_type, ' ', in_entity_id, ')'); LEAVE proc; END IF;

    IF in_operation IN ('C', 'U') THEN
        IF NOT fn_external_id_format_ok(in_provider, in_entity_type, v_value) THEN
            SET err_msg = CONCAT(in_provider, ' ID 형식이 맞지 않습니다. (', COALESCE(v_value, '-'), ')'); LEAVE proc;
        END IF;
        -- 같은 외부 ID가 다른 대상에 붙어 있으면 거절 (어느 쪽이 맞는지 사람이 정해야 한다)
        SELECT CONCAT(entity_type, ' ', entity_id) INTO v_owner
          FROM content_external_id
         WHERE provider = in_provider AND external_id = v_value
           AND NOT (entity_type = in_entity_type AND entity_id = in_entity_id)
         LIMIT 1;
        IF v_owner IS NOT NULL THEN
            SET err_msg = CONCAT(in_provider, ' ', v_value, '는 이미 ', v_owner, '에 연결되어 있습니다.'); LEAVE proc;
        END IF;
    END IF;

    IF in_operation = 'C' THEN
        -- 등록: 같은 대상·제공처에 이미 ID가 있으면 U로 바꾸라고 알린다
        IF EXISTS (SELECT 1 FROM content_external_id WHERE entity_type = in_entity_type AND entity_id = in_entity_id AND provider = in_provider) THEN
            SET err_msg = CONCAT('이미 ', in_provider, ' ID가 연결되어 있습니다. 바꾸려면 수정(U)을 쓰세요. (', v_key, ')'); LEAVE proc;
        END IF;
        INSERT INTO content_external_id (entity_type, entity_id, provider, external_id, match_method, verified)
        VALUES (in_entity_type, in_entity_id, in_provider, v_value, 'MANUAL', 1);
    ELSEIF in_operation = 'U' THEN
        -- 수정: 운영자 확인 값으로 바꾼다
        UPDATE content_external_id
           SET external_id = v_value, match_method = 'MANUAL', verified = 1
         WHERE entity_type = in_entity_type AND entity_id = in_entity_id AND provider = in_provider;
        IF ROW_COUNT() = 0 THEN SET err_msg = CONCAT('수정할 외부 ID가 없습니다. (', v_key, ')'); LEAVE proc; END IF;
    ELSEIF in_operation = 'D' THEN
        -- 삭제: 연결을 끊는다 (작업 로그에 이전 값이 남는다)
        SELECT external_id INTO v_value FROM content_external_id
         WHERE entity_type = in_entity_type AND entity_id = in_entity_id AND provider = in_provider;
        DELETE FROM content_external_id
         WHERE entity_type = in_entity_type AND entity_id = in_entity_id AND provider = in_provider;
        IF ROW_COUNT() = 0 THEN SET err_msg = CONCAT('끊을 외부 ID가 없습니다. (', v_key, ')'); LEAVE proc; END IF;
    ELSE
        SET err_msg = CONCAT('작업 구분은 C, U, D 중 하나여야 합니다. (입력값: ', in_operation, ')'); LEAVE proc;
    END IF;

    -- 인물이 외부 ID로 확인되면 이름만 있는 인물 표시를 끈다 (모든 외부 ID가 끊기면 다시 켠다)
    IF in_entity_type = 'PERSON' THEN
        UPDATE content_person
           SET name_only = IF(EXISTS (SELECT 1 FROM content_external_id e WHERE e.entity_type = 'PERSON' AND e.entity_id = in_entity_id), 0, 1)
         WHERE person_id = in_entity_id;
    END IF;

    -- 작업 로그 기록
    CALL sp_write_work_log('sp_external_id_cud', in_operation, 'content_external_id', v_key, CONCAT('external_id=', COALESCE(v_value, '')), in_worker, in_workip);
    SET rtn_cod = '1';
END$$

-- =====================================================================================
-- 5. 현지화 등록·수정·삭제 (sp_localization_cud)
-- 설명: 한국어(ko)는 한국 서비스 노출 필수라서 지울 수 없다 (DQ004)
-- =====================================================================================
CREATE PROCEDURE sp_localization_cud(
    IN    in_operation   CHAR(1),                                     -- 작업 구분 (C, U, D)
    IN    in_worker      VARCHAR(100),                                -- 작업자
    IN    in_workip      VARCHAR(50),                                 -- 작업 IP
    IN    in_title_id    BIGINT,                                      -- 작품 ID
    IN    in_lang_code   VARCHAR(10),                                 -- 언어 코드 (BCP 47)
    IN    in_local_title VARCHAR(300),                                -- 현지화 제목
    IN    in_overview    TEXT,                                        -- 현지화 줄거리
    INOUT rtn_cod        VARCHAR(1),                                  -- 반환: 1 성공, 0 실패
    INOUT err_msg        VARCHAR(1000)                                -- 반환: 실패 사유 (한국어)
)
proc: BEGIN
    DECLARE v_lang VARCHAR(10) DEFAULT TRIM(in_lang_code);
    DECLARE v_key  VARCHAR(200) DEFAULT CONCAT(in_title_id, ':', TRIM(in_lang_code));

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 err_msg = MESSAGE_TEXT;
        SET rtn_cod = '0', err_msg = LEFT(CONCAT('DB 오류: ', err_msg), 1000);
    END;

    SET rtn_cod = '0', err_msg = '';

    -- 공통 입력값 확인
    IF NULLIF(TRIM(in_operation), '') IS NULL THEN SET err_msg = '작업 구분(in_operation)이 비어 있습니다.'; LEAVE proc; END IF;
    IF v_lang IS NULL OR v_lang NOT REGEXP '^[a-z]{2}(-[A-Z]{2})?$' THEN SET err_msg = CONCAT('언어 코드는 ko, en, zh-TW 형식이어야 합니다. (', COALESCE(v_lang, '-'), ')'); LEAVE proc; END IF;
    IF NOT EXISTS (SELECT 1 FROM content_title WHERE title_id = in_title_id AND status <> 'DELETED') THEN
        SET err_msg = CONCAT('작품이 없거나 삭제되었습니다. (', in_title_id, ')'); LEAVE proc;
    END IF;

    IF in_operation = 'C' THEN
        IF NULLIF(TRIM(in_local_title), '') IS NULL THEN SET err_msg = CONCAT('현지화 제목이 비어 있습니다. (', v_key, ')'); LEAVE proc; END IF;
        IF EXISTS (SELECT 1 FROM content_localization WHERE title_id = in_title_id AND lang_code = v_lang) THEN
            SET err_msg = CONCAT('이미 등록된 언어입니다. 바꾸려면 수정(U)을 쓰세요. (', v_key, ')'); LEAVE proc;
        END IF;
        INSERT INTO content_localization (title_id, lang_code, local_title, overview, source)
        VALUES (in_title_id, v_lang, TRIM(in_local_title), NULLIF(TRIM(in_overview), ''), 'MANUAL');
    ELSEIF in_operation = 'U' THEN
        IF NULLIF(TRIM(in_local_title), '') IS NULL THEN SET err_msg = CONCAT('현지화 제목이 비어 있습니다. (', v_key, ')'); LEAVE proc; END IF;
        UPDATE content_localization
           SET local_title = TRIM(in_local_title), overview = NULLIF(TRIM(in_overview), ''), source = 'MANUAL'
         WHERE title_id = in_title_id AND lang_code = v_lang;
        IF ROW_COUNT() = 0 THEN SET err_msg = CONCAT('수정할 현지화가 없습니다. (', v_key, ')'); LEAVE proc; END IF;
    ELSEIF in_operation = 'D' THEN
        IF v_lang = 'ko' THEN SET err_msg = '한국어 현지화는 지울 수 없습니다. 수정(U)을 쓰세요.'; LEAVE proc; END IF;
        DELETE FROM content_localization WHERE title_id = in_title_id AND lang_code = v_lang;
        IF ROW_COUNT() = 0 THEN SET err_msg = CONCAT('삭제할 현지화가 없습니다. (', v_key, ')'); LEAVE proc; END IF;
    ELSE
        SET err_msg = CONCAT('작업 구분은 C, U, D 중 하나여야 합니다. (입력값: ', in_operation, ')'); LEAVE proc;
    END IF;

    -- 메타 버전을 올린다. source=MANUAL로 남겨서 레거시 동기화(V5)가 이 현지화를 덮지 않는다
    UPDATE content_title SET meta_version = meta_version + 1 WHERE title_id = in_title_id;

    -- 작업 로그 기록
    CALL sp_write_work_log('sp_localization_cud', in_operation, 'content_localization', v_key, in_local_title, in_worker, in_workip);
    SET rtn_cod = '1';
END$$

DELIMITER ;
