-- =====================================================================================
-- Flyway 마이그레이션: 배치 실행·SLA·데이터 품질 운영 체계
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V8
-- 작성일: 2026-11-22
-- 설명: 배치 작업마다 SLA(허용 시간·최대 지연)를 정하고 실행 기록에 위반 여부를 남긴다.
--       데이터 품질 규칙을 테이블로 관리하고, 규칙별 위반 건수와 예시 키를 결과로 남긴다.
--       규칙의 임계값을 넘으면 실패(FAIL)로 판정해 운영자가 보게 한다
-- =====================================================================================

-- =====================================================================================
-- 1. 배치 작업 정의 (batch_job)
-- 설명: 작업별 SLA. max_minutes를 넘기면 실행 SLA 위반, 마지막 성공이 max_gap_minutes보다 오래되면 신선도 위반
-- =====================================================================================
CREATE TABLE batch_job (
    job_name          VARCHAR(50)  NOT NULL                               COMMENT '작업 이름',
    description       VARCHAR(200) NOT NULL                               COMMENT '작업 설명',
    max_minutes       INT          NOT NULL                               COMMENT 'SLA: 한 번 실행 허용 시간(분)',
    max_gap_minutes   INT          NOT NULL                               COMMENT 'SLA: 마지막 성공 후 허용 간격(분). 넘으면 데이터가 오래된 것',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1                     COMMENT '사용 여부 (1:사용, 0:중지)',
    PRIMARY KEY (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='배치 작업 정의와 SLA';

INSERT INTO batch_job (job_name, description, max_minutes, max_gap_minutes) VALUES
 ('LEGACY_SYNC',   '레거시 변경분 동기화 (1분마다)',            1,    10),
 ('LEGACY_VERIFY', '원본·신규 대조 (매시간)',                    10,   90),
 ('QUALITY_CHECK', '데이터 품질 점검 (매일 새벽)',               30,   1500);

-- =====================================================================================
-- 2. 배치 실행 기록 (batch_job_run)
-- 설명: 실행 한 번의 시작·종료·처리 건수·실패 사유·SLA 위반 여부
-- =====================================================================================
CREATE TABLE batch_job_run (
    job_run_id        BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '실행 ID (자동증가)',
    job_name          VARCHAR(50)  NOT NULL                               COMMENT '작업 이름',
    status            VARCHAR(10)  NOT NULL DEFAULT 'RUNNING'             COMMENT '상태 (RUNNING:실행 중, SUCCESS:성공, FAILED:실패)',
    started_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '시작일시',
    finished_at       DATETIME(3)                                         COMMENT '종료일시',
    processed_count   INT          NOT NULL DEFAULT 0                     COMMENT '처리 건수',
    error_message     VARCHAR(2000)                                       COMMENT '실패 사유',
    sla_breached      TINYINT(1)   NOT NULL DEFAULT 0                     COMMENT '실행 SLA 위반 여부 (1:허용 시간 초과)',
    worker            VARCHAR(100) NOT NULL                               COMMENT '실행한 곳 (서버 이름, MANUAL)',
    PRIMARY KEY (job_run_id),
    CONSTRAINT ck_batch_job_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT fk_batch_job_run_job FOREIGN KEY (job_name) REFERENCES batch_job (job_name),
    KEY ix_batch_job_run_job (job_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='배치 실행 기록';

-- =====================================================================================
-- 3. 데이터 품질 규칙 (dq_rule)
-- 설명: threshold는 허용하는 위반 건수. 위반 건수가 threshold보다 크면 FAIL
-- =====================================================================================
CREATE TABLE dq_rule (
    rule_code         VARCHAR(10)  NOT NULL                               COMMENT '규칙 코드',
    rule_name         VARCHAR(100) NOT NULL                               COMMENT '규칙 이름',
    description       VARCHAR(300) NOT NULL                               COMMENT '무엇을 왜 보는가',
    severity          VARCHAR(5)   NOT NULL                               COMMENT '심각도 (HIGH:바로 확인, LOW:주간 정리)',
    threshold         INT          NOT NULL DEFAULT 0                     COMMENT '허용 위반 건수',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1                     COMMENT '사용 여부',
    PRIMARY KEY (rule_code),
    CONSTRAINT ck_dq_rule_severity CHECK (severity IN ('HIGH', 'LOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='데이터 품질 규칙';

INSERT INTO dq_rule (rule_code, rule_name, description, severity, threshold) VALUES
 ('DQ001', 'IMDB ID 형식',          'IMDB 작품은 tt, 인물은 nm 뒤에 숫자 7자리 이상이어야 한다', 'HIGH', 0),
 ('DQ002', 'TMDB·KOBIS ID 형식',    'TMDB는 숫자, KOBIS 영화코드는 숫자 8자리여야 한다', 'HIGH', 0),
 ('DQ003', '외부 ID 없는 작품',     '삭제되지 않은 작품 중 TMDB·IMDB·KOBIS ID가 하나도 없는 작품. 글로벌 메타와 연결되지 않는다', 'LOW', 100),
 ('DQ004', '한국어 현지화 누락',    '한국 서비스 노출 작품에 ko 제목이 없다', 'HIGH', 0),
 ('DQ005', '시즌 없는 시리즈',      '시리즈인데 시즌이 하나도 없다. 회차 단위 서비스가 안 된다', 'LOW', 50),
 ('DQ006', '에피소드 번호 빠짐',    '시즌 안 회차 번호가 1부터 이어지지 않는다 (예: 1, 2, 4)', 'HIGH', 0),
 ('DQ007', '이름만 있는 인물',      '외부 인물 ID 없이 이름으로만 만든 인물. 동명이인을 가를 수 없다', 'LOW', 1000),
 ('DQ008', '출연진 없는 영화',      '삭제되지 않은 영화에 출연·감독 정보가 없다', 'LOW', 20),
 ('DQ009', '미반영 레거시 변경',    '10분 넘게 반영되지 않은 레거시 변경분. 동기화가 멈췄을 수 있다', 'HIGH', 0);

-- =====================================================================================
-- 4. 데이터 품질 결과 (dq_result)
-- 설명: 점검 실행(batch_job_run) 하나에 규칙별 한 행
-- =====================================================================================
CREATE TABLE dq_result (
    job_run_id        BIGINT       NOT NULL                               COMMENT '점검 실행 ID',
    rule_code         VARCHAR(10)  NOT NULL                               COMMENT '규칙 코드',
    violation_count   INT          NOT NULL                               COMMENT '위반 건수',
    threshold         INT          NOT NULL                               COMMENT '점검 당시 허용 건수',
    result            VARCHAR(4)   NOT NULL                               COMMENT '판정 (PASS, FAIL)',
    sample_keys       VARCHAR(500)                                        COMMENT '위반 예시 키 (최대 10개)',
    checked_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '점검일시',
    PRIMARY KEY (job_run_id, rule_code),
    CONSTRAINT fk_dq_result_run FOREIGN KEY (job_run_id) REFERENCES batch_job_run (job_run_id),
    CONSTRAINT fk_dq_result_rule FOREIGN KEY (rule_code) REFERENCES dq_rule (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='데이터 품질 점검 결과';

DELIMITER $$

-- =====================================================================================
-- 5. 배치 실행 시작 (sp_batch_run_start)
-- 설명: 같은 작업이 이미 RUNNING이면 겹쳐 돌지 않게 거절한다. 단 허용 시간의 3배를 넘긴 RUNNING은 멈춘 것으로 보고 FAILED로 정리한다
-- =====================================================================================
CREATE PROCEDURE sp_batch_run_start(
    IN  p_job_name   VARCHAR(50),                                     -- 작업 이름
    IN  p_worker     VARCHAR(100),                                    -- 실행한 곳
    OUT o_job_run_id BIGINT,                                          -- 반환: 실행 ID (거절이면 NULL)
    OUT o_rtn_cod    INT,                                             -- 반환: 1 시작, 0 거절
    OUT o_err_msg    VARCHAR(300)                                     -- 반환: 거절 사유 (한국어)
)
BEGIN
    DECLARE v_max     INT;
    DECLARE v_enabled TINYINT;
    DECLARE v_running INT;

    SET o_job_run_id = NULL, o_rtn_cod = 0, o_err_msg = NULL;
    SELECT max_minutes, enabled INTO v_max, v_enabled FROM batch_job WHERE job_name = p_job_name;

    IF v_max IS NULL THEN
        SET o_err_msg = CONCAT('등록되지 않은 배치 작업입니다: ', p_job_name);
    ELSEIF v_enabled = 0 THEN
        SET o_err_msg = CONCAT('중지된 배치 작업입니다: ', p_job_name);
    ELSE
        START TRANSACTION;
        -- 멈춘 실행 정리 (서버가 죽어 RUNNING으로 남은 기록)
        UPDATE batch_job_run
           SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP(3), sla_breached = 1,
               error_message = '허용 시간의 3배를 넘겨 멈춘 실행으로 정리했습니다.'
         WHERE job_name = p_job_name AND status = 'RUNNING'
           AND started_at < CURRENT_TIMESTAMP(3) - INTERVAL (v_max * 3) MINUTE;

        -- 작업 정의 행을 잠가 같은 작업의 동시 시작을 한 줄로 세운다
        SELECT max_minutes INTO v_max FROM batch_job WHERE job_name = p_job_name FOR UPDATE;
        SELECT COUNT(*) INTO v_running FROM batch_job_run WHERE job_name = p_job_name AND status = 'RUNNING';
        IF v_running > 0 THEN
            SET o_err_msg = '같은 작업이 이미 실행 중입니다.';
            ROLLBACK;
        ELSE
            INSERT INTO batch_job_run (job_name, worker) VALUES (p_job_name, COALESCE(NULLIF(TRIM(p_worker), ''), 'unknown'));
            SET o_job_run_id = LAST_INSERT_ID(), o_rtn_cod = 1;
            COMMIT;
        END IF;
    END IF;
END$$

-- =====================================================================================
-- 6. 배치 실행 종료 (sp_batch_run_finish)
-- 설명: 성공·실패와 처리 건수를 남기고 실행 SLA 위반을 판정한다
-- =====================================================================================
CREATE PROCEDURE sp_batch_run_finish(
    IN  p_job_run_id BIGINT,                                          -- 실행 ID
    IN  p_success    TINYINT,                                         -- 1 성공, 0 실패
    IN  p_processed  INT,                                             -- 처리 건수
    IN  p_error      VARCHAR(2000),                                   -- 실패 사유
    OUT o_sla_breached TINYINT                                        -- 반환: 실행 SLA 위반 여부
)
BEGIN
    UPDATE batch_job_run r
      JOIN batch_job j ON j.job_name = r.job_name
       SET r.status = IF(p_success = 1, 'SUCCESS', 'FAILED'),
           r.finished_at = CURRENT_TIMESTAMP(3),
           r.processed_count = COALESCE(p_processed, 0),
           r.error_message = IF(p_success = 1, NULL, LEFT(p_error, 2000)),
           r.sla_breached = IF(TIMESTAMPDIFF(SECOND, r.started_at, CURRENT_TIMESTAMP(3)) > j.max_minutes * 60, 1, 0)
     WHERE r.job_run_id = p_job_run_id
       AND r.status = 'RUNNING';

    SELECT sla_breached INTO o_sla_breached FROM batch_job_run WHERE job_run_id = p_job_run_id;
END$$

-- =====================================================================================
-- 7. 데이터 품질 점검 (sp_run_quality_checks)
-- 설명: 사용 중인 규칙을 모두 돌려 dq_result에 남긴다. 규칙이 늘면 이 프로시저에 블록 하나를 더한다
-- =====================================================================================
CREATE PROCEDURE sp_run_quality_checks(
    IN  p_job_run_id   BIGINT,                                        -- 점검 실행 ID (sp_batch_run_start로 받은 값)
    OUT o_failed_rules INT                                            -- 반환: FAIL 규칙 수
)
BEGIN
    DROP TEMPORARY TABLE IF EXISTS tmp_dq_violation;
    CREATE TEMPORARY TABLE tmp_dq_violation (
        rule_code VARCHAR(10)  NOT NULL,
        item_key  VARCHAR(100) NOT NULL
    );

    -- DQ001 IMDB ID 형식
    INSERT INTO tmp_dq_violation
    SELECT 'DQ001', CONCAT(entity_type, ':', entity_id, '=', external_id)
      FROM content_external_id
     WHERE provider = 'IMDB'
       AND NOT ((entity_type IN ('TITLE', 'SEASON', 'EPISODE') AND external_id REGEXP '^tt[0-9]{7,}$')
             OR (entity_type = 'PERSON' AND external_id REGEXP '^nm[0-9]{7,}$'));

    -- DQ002 TMDB·KOBIS ID 형식
    INSERT INTO tmp_dq_violation
    SELECT 'DQ002', CONCAT(provider, ':', external_id)
      FROM content_external_id
     WHERE (provider LIKE 'TMDB%' AND external_id NOT REGEXP '^[0-9]+$')
        OR (provider = 'KOBIS' AND external_id NOT REGEXP '^[0-9]{8}$');

    -- DQ003 외부 ID 없는 작품
    INSERT INTO tmp_dq_violation
    SELECT 'DQ003', CONCAT('title:', ct.title_id)
      FROM content_title ct
     WHERE ct.status <> 'DELETED'
       AND NOT EXISTS (SELECT 1 FROM content_external_id e WHERE e.entity_type = 'TITLE' AND e.entity_id = ct.title_id);

    -- DQ004 한국어 현지화 누락
    INSERT INTO tmp_dq_violation
    SELECT 'DQ004', CONCAT('title:', ct.title_id)
      FROM content_title ct
     WHERE ct.status <> 'DELETED'
       AND NOT EXISTS (SELECT 1 FROM content_localization l WHERE l.title_id = ct.title_id AND l.lang_code = 'ko');

    -- DQ005 시즌 없는 시리즈
    INSERT INTO tmp_dq_violation
    SELECT 'DQ005', CONCAT('title:', ct.title_id)
      FROM content_title ct
     WHERE ct.title_type = 'SERIES' AND ct.status <> 'DELETED'
       AND NOT EXISTS (SELECT 1 FROM content_season s WHERE s.title_id = ct.title_id);

    -- DQ006 에피소드 번호 빠짐 (최대 번호 ≠ 개수이거나 1부터 시작하지 않음)
    INSERT INTO tmp_dq_violation
    SELECT 'DQ006', CONCAT('season:', e.season_id)
      FROM content_episode e
     GROUP BY e.season_id
    HAVING MIN(e.episode_no) <> 1 OR MAX(e.episode_no) <> COUNT(*);

    -- DQ007 이름만 있는 인물
    INSERT INTO tmp_dq_violation
    SELECT 'DQ007', CONCAT('person:', p.person_id)
      FROM content_person p
     WHERE p.name_only = 1;

    -- DQ008 출연진 없는 영화
    INSERT INTO tmp_dq_violation
    SELECT 'DQ008', CONCAT('title:', ct.title_id)
      FROM content_title ct
     WHERE ct.title_type = 'MOVIE' AND ct.status <> 'DELETED'
       AND NOT EXISTS (SELECT 1 FROM content_credit c WHERE c.title_id = ct.title_id);

    -- DQ009 미반영 레거시 변경 (10분 초과)
    INSERT INTO tmp_dq_violation
    SELECT 'DQ009', CONCAT(l.source_table, ':', l.source_seq)
      FROM legacy_change_log l
     WHERE l.applied_run_id IS NULL
       AND l.changed_at < CURRENT_TIMESTAMP(3) - INTERVAL 10 MINUTE;

    -- 규칙별 결과 (위반이 없는 규칙도 PASS로 남긴다)
    DELETE FROM dq_result WHERE job_run_id = p_job_run_id;
    INSERT INTO dq_result (job_run_id, rule_code, violation_count, threshold, result, sample_keys)
    SELECT p_job_run_id, r.rule_code, COALESCE(v.cnt, 0), r.threshold,
           IF(COALESCE(v.cnt, 0) > r.threshold, 'FAIL', 'PASS'), v.samples
      FROM dq_rule r
      LEFT JOIN (SELECT rule_code, COUNT(*) AS cnt,
                        SUBSTRING_INDEX(GROUP_CONCAT(item_key ORDER BY item_key SEPARATOR ', '), ', ', 10) AS samples
                   FROM tmp_dq_violation
                  GROUP BY rule_code) v ON v.rule_code = r.rule_code
     WHERE r.enabled = 1;

    SELECT COUNT(*) INTO o_failed_rules FROM dq_result WHERE job_run_id = p_job_run_id AND result = 'FAIL';
    DROP TEMPORARY TABLE IF EXISTS tmp_dq_violation;
END$$

-- =====================================================================================
-- 8. SLA 신선도 점검 (sp_check_sla_freshness)
-- 설명: 작업별 마지막 성공이 max_gap_minutes보다 오래되었는지 본다. 모니터링·알림이 이 결과를 읽는다
-- =====================================================================================
CREATE PROCEDURE sp_check_sla_freshness()
BEGIN
    SELECT j.job_name,
           j.max_gap_minutes,
           MAX(IF(r.status = 'SUCCESS', r.finished_at, NULL)) AS last_success_at,
           TIMESTAMPDIFF(MINUTE, MAX(IF(r.status = 'SUCCESS', r.finished_at, NULL)), CURRENT_TIMESTAMP(3)) AS minutes_since_success,
           COALESCE(SUM(r.status = 'FAILED' AND r.started_at > CURRENT_TIMESTAMP(3) - INTERVAL 1 DAY), 0) AS failures_24h,
           COALESCE(SUM(r.sla_breached = 1 AND r.started_at > CURRENT_TIMESTAMP(3) - INTERVAL 1 DAY), 0) AS sla_breaches_24h,
           IF(MAX(IF(r.status = 'SUCCESS', r.finished_at, NULL)) IS NULL
              OR MAX(IF(r.status = 'SUCCESS', r.finished_at, NULL)) < CURRENT_TIMESTAMP(3) - INTERVAL j.max_gap_minutes MINUTE,
              'STALE', 'OK') AS freshness
      FROM batch_job j
      LEFT JOIN batch_job_run r ON r.job_name = j.job_name
     WHERE j.enabled = 1
     GROUP BY j.job_name, j.max_gap_minutes;
END$$

DELIMITER ;
