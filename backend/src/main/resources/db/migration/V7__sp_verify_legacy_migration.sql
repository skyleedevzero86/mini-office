-- =====================================================================================
-- Flyway 마이그레이션: 원본·신규 대조 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V7
-- 작성일: 2026-11-22
-- 설명: 레거시 원본을 지금 상태로 다시 검증하고, 적재 대상(ERROR 아님)과 신규 작품을 건수·내용 지문(CRC32)으로 대조한다.
--       읽기 전환은 세 원본 테이블 모두 누락·불일치·고아가 0일 때만 한다 (전환 판단은 서비스가 migration_verify를 보고 한다)
-- =====================================================================================

DELIMITER $$

-- =====================================================================================
-- 1. 원본·신규 대조 (sp_verify_legacy_migration)
-- 설명: mode=VERIFY 작업으로 호출한다. 데이터는 바꾸지 않고 migration_issue·migration_verify만 남긴다
-- =====================================================================================
CREATE PROCEDURE sp_verify_legacy_migration(
    IN  p_run_id   BIGINT,                                            -- 대조 작업 ID (mode=VERIFY)
    OUT o_missing  INT,                                               -- 반환: 세 테이블 합계 누락 건수
    OUT o_mismatch INT,                                               -- 반환: 세 테이블 합계 내용 불일치 건수
    OUT o_orphan   INT                                                -- 반환: 세 테이블 합계 고아 건수
)
BEGIN
    DECLARE v_mode   VARCHAR(10);
    DECLARE v_errors INT;
    DECLARE v_infos  INT;

    SELECT mode INTO v_mode FROM migration_run WHERE run_id = p_run_id;
    IF v_mode IS NULL OR v_mode <> 'VERIFY' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '대조 작업이 아니거나 찾을 수 없습니다.';
    END IF;

    UPDATE migration_run SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP(3), attempt_count = attempt_count + 1
     WHERE run_id = p_run_id;

    -- 지금 원본 상태로 다시 검증 (적재 대상 판정 기준)
    DELETE FROM migration_work_key WHERE run_id = p_run_id;
    INSERT INTO migration_work_key (run_id, source_table, source_seq)
    SELECT p_run_id, 'movies', movie_seq FROM movies
    UNION ALL SELECT p_run_id, 'MergedTVShow', mgSeq FROM MergedTVShow
    UNION ALL SELECT p_run_id, 'cartoon_video', ctSeq FROM cartoon_video;
    CALL sp_validate_legacy_keys(p_run_id, v_errors, v_infos);
    DELETE FROM migration_work_key WHERE run_id = p_run_id;

    -- 원본 지문: V5 적재와 같은 변환을 거친 값으로 만든다
    DROP TEMPORARY TABLE IF EXISTS tmp_verify_source;
    CREATE TEMPORARY TABLE tmp_verify_source (
        origin_system VARCHAR(30)  NOT NULL,
        origin_key    VARCHAR(100) NOT NULL,
        fingerprint   BIGINT       NOT NULL,
        PRIMARY KEY (origin_system, origin_key)
    );
    INSERT INTO tmp_verify_source (origin_system, origin_key, fingerprint)
    SELECT 'LEGACY_MOVIES', CAST(m.movie_seq AS CHAR),
           CRC32(CONCAT_WS('|', TRIM(m.movie_title), COALESCE(fn_try_date(m.movie_open_date), '-'),
                           COALESCE(fn_try_runtime(m.movie_runtime), '-'),
                           IF(fn_try_date(m.movie_open_date) > CURDATE(), 'PLANNED', 'RELEASED')))
      FROM movies m
     WHERE NOT fn_row_has_error(p_run_id, 'movies', m.movie_seq)
    UNION ALL
    SELECT 'LEGACY_TV', CAST(t.mgSeq AS CHAR),
           CRC32(CONCAT_WS('|', COALESCE(NULLIF(TRIM(t.originalName), ''), TRIM(t.tvName)),
                           COALESCE(fn_try_date(t.first_air_date), '-'), '-', fn_map_code('TV_STATUS', t.tvStatus)))
      FROM MergedTVShow t
     WHERE NOT fn_row_has_error(p_run_id, 'MergedTVShow', t.mgSeq)
    UNION ALL
    SELECT 'LEGACY_CARTOON', CAST(c.ctSeq AS CHAR),
           CRC32(CONCAT_WS('|', COALESCE(NULLIF(TRIM(c.englishTitle), ''), TRIM(c.cartoonTitle)),
                           COALESCE(fn_try_date(c.airing_date), '-'), '-', fn_map_code('CARTOON_STATUS', c.status)))
      FROM cartoon_video c
     WHERE NOT fn_row_has_error(p_run_id, 'cartoon_video', c.ctSeq);

    -- 신규 지문
    DROP TEMPORARY TABLE IF EXISTS tmp_verify_target;
    CREATE TEMPORARY TABLE tmp_verify_target (
        origin_system VARCHAR(30)  NOT NULL,
        origin_key    VARCHAR(100) NOT NULL,
        fingerprint   BIGINT       NOT NULL,
        PRIMARY KEY (origin_system, origin_key)
    );
    INSERT INTO tmp_verify_target (origin_system, origin_key, fingerprint)
    SELECT ct.origin_system, ct.origin_key,
           CRC32(CONCAT_WS('|', ct.original_title, COALESCE(ct.release_date, '-'), COALESCE(ct.runtime_min, '-'), ct.status))
      FROM content_title ct
     WHERE ct.origin_system IN ('LEGACY_MOVIES', 'LEGACY_TV', 'LEGACY_CARTOON')
       AND ct.status <> 'DELETED';

    -- 원본 테이블별 결과
    INSERT INTO migration_verify (run_id, source_table, source_count, target_count, missing_count, mismatch_count, orphan_count)
    SELECT p_run_id,
           CASE k.origin_system WHEN 'LEGACY_MOVIES' THEN 'movies' WHEN 'LEGACY_TV' THEN 'MergedTVShow' ELSE 'cartoon_video' END,
           (SELECT COUNT(*) FROM tmp_verify_source s WHERE s.origin_system = k.origin_system),
           (SELECT COUNT(*) FROM tmp_verify_target t WHERE t.origin_system = k.origin_system),
           0, 0, 0
      FROM (SELECT 'LEGACY_MOVIES' AS origin_system UNION ALL SELECT 'LEGACY_TV' UNION ALL SELECT 'LEGACY_CARTOON') k;

    -- 누락: 원본(적재 대상)에는 있는데 신규에 없음
    UPDATE migration_verify v
      JOIN (SELECT s.origin_system, COUNT(*) AS cnt
              FROM tmp_verify_source s
              LEFT JOIN tmp_verify_target t ON t.origin_system = s.origin_system AND t.origin_key = s.origin_key
             WHERE t.origin_key IS NULL
             GROUP BY s.origin_system) x
        ON v.source_table = CASE x.origin_system WHEN 'LEGACY_MOVIES' THEN 'movies' WHEN 'LEGACY_TV' THEN 'MergedTVShow' ELSE 'cartoon_video' END
       SET v.missing_count = x.cnt
     WHERE v.run_id = p_run_id;

    -- 불일치: 양쪽에 있지만 지문이 다름
    UPDATE migration_verify v
      JOIN (SELECT s.origin_system, COUNT(*) AS cnt
              FROM tmp_verify_source s
              JOIN tmp_verify_target t ON t.origin_system = s.origin_system AND t.origin_key = s.origin_key
             WHERE t.fingerprint <> s.fingerprint
             GROUP BY s.origin_system) x
        ON v.source_table = CASE x.origin_system WHEN 'LEGACY_MOVIES' THEN 'movies' WHEN 'LEGACY_TV' THEN 'MergedTVShow' ELSE 'cartoon_video' END
       SET v.mismatch_count = x.cnt
     WHERE v.run_id = p_run_id;

    -- 고아: 신규에는 살아 있는데 원본(적재 대상)에는 없음 (원본 삭제를 못 따라갔거나 지금 ERROR가 된 행)
    UPDATE migration_verify v
      JOIN (SELECT t.origin_system, COUNT(*) AS cnt
              FROM tmp_verify_target t
              LEFT JOIN tmp_verify_source s ON s.origin_system = t.origin_system AND s.origin_key = t.origin_key
             WHERE s.origin_key IS NULL
             GROUP BY t.origin_system) x
        ON v.source_table = CASE x.origin_system WHEN 'LEGACY_MOVIES' THEN 'movies' WHEN 'LEGACY_TV' THEN 'MergedTVShow' ELSE 'cartoon_video' END
       SET v.orphan_count = x.cnt
     WHERE v.run_id = p_run_id;

    SELECT COALESCE(SUM(missing_count), 0), COALESCE(SUM(mismatch_count), 0), COALESCE(SUM(orphan_count), 0)
      INTO o_missing, o_mismatch, o_orphan
      FROM migration_verify
     WHERE run_id = p_run_id;

    UPDATE migration_run
       SET status = IF(o_missing + o_mismatch + o_orphan = 0, 'COMPLETED', 'COMPLETED_WITH_ISSUES'),
           error_count = v_errors, info_count = v_infos, finished_at = CURRENT_TIMESTAMP(3)
     WHERE run_id = p_run_id;

    DROP TEMPORARY TABLE IF EXISTS tmp_verify_source;
    DROP TEMPORARY TABLE IF EXISTS tmp_verify_target;
END$$

DELIMITER ;
