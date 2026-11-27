-- =====================================================================================
-- Flyway 마이그레이션: 적재 전 검증 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V4
-- 작성일: 2026-11-22
-- 설명: 레거시 movies, MergedTVShow, cartoon_video 행의 필수값 누락, 형식 오류, 중복, 코드 대응 실패,
--       외부 ID 없음을 검사해 migration_issue에 사유를 남긴다. ERROR 행은 적재하지 않는다
-- =====================================================================================

DELIMITER $$

-- =====================================================================================
-- 1. 오류 행 여부 (fn_row_has_error)
-- 설명: 적재 프로시저가 ERROR 행을 건너뛸 때 쓴다
-- =====================================================================================
CREATE FUNCTION fn_row_has_error(
    p_run_id BIGINT,                                                  -- 이관 작업 ID
    p_table  VARCHAR(30),                                             -- 원본 테이블
    p_seq    INT                                                      -- 원본 순번
) RETURNS TINYINT
READS SQL DATA
BEGIN
    RETURN EXISTS (SELECT 1
                     FROM migration_issue i
                    WHERE i.run_id = p_run_id
                      AND i.source_table = p_table
                      AND i.source_key = CAST(p_seq AS CHAR)
                      AND i.severity = 'ERROR');
END$$

-- =====================================================================================
-- 2. 처리 대상 키 검증 (sp_validate_legacy_keys)
-- 설명: migration_work_key에 담긴 이번 묶음의 키만 검사한다. 같은 키를 다시 검사하면 이전 결과를 지우고 새로 남긴다
-- =====================================================================================
CREATE PROCEDURE sp_validate_legacy_keys(
    IN  p_run_id      BIGINT,                                         -- 이관 작업 ID
    OUT o_error_count INT,                                            -- 반환: 이번 검사의 오류(ERROR) 건수
    OUT o_info_count  INT                                             -- 반환: 이번 검사의 안내(INFO) 건수
)
BEGIN
    -- 이전 검증 결과 삭제 (같은 키 재검사)
    DELETE i FROM migration_issue i
      JOIN migration_work_key w ON w.run_id = i.run_id AND w.source_table = i.source_table AND CAST(w.source_seq AS CHAR) = i.source_key
     WHERE i.run_id = p_run_id;

    -- 영화: 필수값(제목) 누락
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'movies', m.movie_seq, 'REQUIRED_MISSING', 'ERROR', '필수값 누락: 영화 제목'
      FROM migration_work_key w
      JOIN movies m ON m.movie_seq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'movies'
       AND NULLIF(TRIM(m.movie_title), '') IS NULL;

    -- 영화: 개봉일 형식 오류
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'movies', m.movie_seq, 'INVALID_FORMAT', 'ERROR', CONCAT('개봉일 형식 오류: ', m.movie_open_date)
      FROM migration_work_key w
      JOIN movies m ON m.movie_seq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'movies'
       AND NULLIF(TRIM(m.movie_open_date), '') IS NOT NULL
       AND fn_try_date(m.movie_open_date) IS NULL;

    -- 영화: 같은 제목 + 같은 개봉일 중복 (가장 작은 순번만 남기고 나머지는 담당자 확인)
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'movies', m.movie_seq, 'DUPLICATE_SOURCE_KEY', 'ERROR',
           CONCAT('제목·개봉일 중복: movie_seq ', f.first_seq, '와 같은 작품으로 보입니다. 어느 행이 맞는지 확인이 필요합니다.')
      FROM migration_work_key w
      JOIN movies m ON m.movie_seq = w.source_seq
      JOIN (SELECT TRIM(movie_title) AS title, TRIM(movie_open_date) AS open_date, MIN(movie_seq) AS first_seq
              FROM movies
             WHERE NULLIF(TRIM(movie_title), '') IS NOT NULL
             GROUP BY TRIM(movie_title), TRIM(movie_open_date)
            HAVING COUNT(*) > 1) f
        ON f.title = TRIM(m.movie_title) AND f.open_date <=> TRIM(m.movie_open_date)
     WHERE w.run_id = p_run_id AND w.source_table = 'movies'
       AND m.movie_seq <> f.first_seq;

    -- 영화: 제작 국가 대응 실패 (작품은 적재하고 국가만 비운다)
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'movies', m.movie_seq, 'CODE_MAPPING_FAILED', 'INFO',
           CONCAT('제작 국가 ''', TRIM(m.movie_country), '''에 대응하는 ISO 코드가 없어 국가를 비워 둡니다.')
      FROM migration_work_key w
      JOIN movies m ON m.movie_seq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'movies'
       AND NULLIF(TRIM(m.movie_country), '') IS NOT NULL
       AND fn_map_code('COUNTRY_KO', m.movie_country) IS NULL;

    -- 영화: 상영 시간 해석 실패
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'movies', m.movie_seq, 'INVALID_FORMAT', 'INFO',
           CONCAT('상영 시간 ''', TRIM(m.movie_runtime), '''을 분으로 바꿀 수 없어 비워 둡니다.')
      FROM migration_work_key w
      JOIN movies m ON m.movie_seq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'movies'
       AND NULLIF(TRIM(m.movie_runtime), '') IS NOT NULL
       AND fn_try_runtime(m.movie_runtime) IS NULL;

    -- 영화: 외부 표준 ID 없음 (레거시 movies에는 TMDB·KOBIS ID 칸이 없다)
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'movies', m.movie_seq, 'EXTERNAL_ID_MISSING', 'INFO',
           '레거시에 TMDB·KOBIS ID가 없습니다. 외부 ID 매칭 대기로 적재합니다.'
      FROM migration_work_key w
      JOIN movies m ON m.movie_seq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'movies';

    -- TV: 필수값(제목) 누락
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'MergedTVShow', t.mgSeq, 'REQUIRED_MISSING', 'ERROR', '필수값 누락: TV 제목'
      FROM migration_work_key w
      JOIN MergedTVShow t ON t.mgSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'MergedTVShow'
       AND NULLIF(TRIM(t.tvName), '') IS NULL;

    -- TV: 같은 TMDB TV ID가 여러 행 (가장 작은 순번만 남긴다)
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'MergedTVShow', t.mgSeq, 'DUPLICATE_EXTERNAL_ID', 'ERROR',
           CONCAT('TMDB TV ID ', TRIM(t.tvNumber), '가 mgSeq ', f.first_seq, '와 겹칩니다.')
      FROM migration_work_key w
      JOIN MergedTVShow t ON t.mgSeq = w.source_seq
      JOIN (SELECT TRIM(tvNumber) AS tv_number, MIN(mgSeq) AS first_seq
              FROM MergedTVShow
             WHERE NULLIF(TRIM(tvNumber), '') IS NOT NULL
             GROUP BY TRIM(tvNumber)
            HAVING COUNT(*) > 1) f
        ON f.tv_number = TRIM(t.tvNumber)
     WHERE w.run_id = p_run_id AND w.source_table = 'MergedTVShow'
       AND t.mgSeq <> f.first_seq;

    -- TV: 상영 상태 코드 대응 실패
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'MergedTVShow', t.mgSeq, 'CODE_MAPPING_FAILED', 'ERROR',
           CONCAT('상영 상태 ''', COALESCE(TRIM(t.tvStatus), '-'), '''에 대응하는 새 상태가 없습니다.')
      FROM migration_work_key w
      JOIN MergedTVShow t ON t.mgSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'MergedTVShow'
       AND fn_map_code('TV_STATUS', t.tvStatus) IS NULL;

    -- TV: 첫 방영일 형식 오류
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'MergedTVShow', t.mgSeq, 'INVALID_FORMAT', 'ERROR', CONCAT('첫 방영일 형식 오류: ', t.first_air_date)
      FROM migration_work_key w
      JOIN MergedTVShow t ON t.mgSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'MergedTVShow'
       AND NULLIF(TRIM(t.first_air_date), '') IS NOT NULL
       AND fn_try_date(t.first_air_date) IS NULL;

    -- TV: TMDB TV ID 없음·숫자 아님
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'MergedTVShow', t.mgSeq, 'EXTERNAL_ID_MISSING', 'INFO',
           CONCAT('TMDB TV ID ''', COALESCE(TRIM(t.tvNumber), ''), '''가 비었거나 숫자가 아니어서 외부 ID 없이 적재합니다.')
      FROM migration_work_key w
      JOIN MergedTVShow t ON t.mgSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'MergedTVShow'
       AND COALESCE(TRIM(t.tvNumber), '') NOT REGEXP '^[0-9]+$';

    -- 애니: 필수값(제목) 누락
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'cartoon_video', c.ctSeq, 'REQUIRED_MISSING', 'ERROR', '필수값 누락: 애니 제목'
      FROM migration_work_key w
      JOIN cartoon_video c ON c.ctSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'cartoon_video'
       AND NULLIF(TRIM(c.cartoonTitle), '') IS NULL;

    -- 애니: 방영 상태 대응 실패
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'cartoon_video', c.ctSeq, 'CODE_MAPPING_FAILED', 'ERROR',
           CONCAT('방영 상태 ''', COALESCE(TRIM(c.status), '-'), '''에 대응하는 새 상태가 없습니다.')
      FROM migration_work_key w
      JOIN cartoon_video c ON c.ctSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'cartoon_video'
       AND fn_map_code('CARTOON_STATUS', c.status) IS NULL;

    -- 애니: 방영일 형식 오류
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'cartoon_video', c.ctSeq, 'INVALID_FORMAT', 'ERROR', CONCAT('방영일 형식 오류: ', c.airing_date)
      FROM migration_work_key w
      JOIN cartoon_video c ON c.ctSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'cartoon_video'
       AND NULLIF(TRIM(c.airing_date), '') IS NOT NULL
       AND fn_try_date(c.airing_date) IS NULL;

    -- 애니: 화수 범위 오류 (작품은 적재하고 에피소드만 만들지 않는다)
    INSERT INTO migration_issue (run_id, source_table, source_key, issue_type, severity, message)
    SELECT p_run_id, 'cartoon_video', c.ctSeq, 'EPISODE_RANGE_INVALID', 'INFO',
           CONCAT('화수 범위 ', COALESCE(c.episode_start, '-'), '~', COALESCE(c.episode_end, '-'), '를 해석할 수 없어 에피소드를 만들지 않습니다.')
      FROM migration_work_key w
      JOIN cartoon_video c ON c.ctSeq = w.source_seq
     WHERE w.run_id = p_run_id AND w.source_table = 'cartoon_video'
       AND NOT (TRIM(c.episode_start) REGEXP '^[0-9]{1,4}$'
                AND TRIM(c.episode_end) REGEXP '^[0-9]{1,4}$'
                AND CAST(TRIM(c.episode_start) AS UNSIGNED) BETWEEN 1 AND CAST(TRIM(c.episode_end) AS UNSIGNED));

    -- 이번 검사 결과 집계
    SELECT COALESCE(SUM(i.severity = 'ERROR'), 0), COALESCE(SUM(i.severity = 'INFO'), 0)
      INTO o_error_count, o_info_count
      FROM migration_issue i
      JOIN migration_work_key w ON w.run_id = i.run_id AND w.source_table = i.source_table AND CAST(w.source_seq AS CHAR) = i.source_key
     WHERE i.run_id = p_run_id;
END$$

-- =====================================================================================
-- 3. 전체 사전 검증 (sp_validate_legacy_migration)
-- 설명: 적재 전에 레거시 전체를 한 번 검사해 보고서를 만든다. 데이터는 바꾸지 않는다
-- =====================================================================================
CREATE PROCEDURE sp_validate_legacy_migration(
    IN  p_run_id      BIGINT,                                         -- 이관 작업 ID
    OUT o_error_count INT,                                            -- 반환: 전체 오류(ERROR) 건수
    OUT o_info_count  INT                                             -- 반환: 전체 안내(INFO) 건수
)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM migration_run WHERE run_id = p_run_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '이관 작업을 찾을 수 없습니다.';
    END IF;

    -- 전체 키를 처리 대상으로 담는다
    DELETE FROM migration_work_key WHERE run_id = p_run_id;
    INSERT INTO migration_work_key (run_id, source_table, source_seq)
    SELECT p_run_id, 'movies', movie_seq FROM movies
    UNION ALL SELECT p_run_id, 'MergedTVShow', mgSeq FROM MergedTVShow
    UNION ALL SELECT p_run_id, 'cartoon_video', ctSeq FROM cartoon_video;

    CALL sp_validate_legacy_keys(p_run_id, o_error_count, o_info_count);

    -- 처리 대상 비우기 (사전 검증은 적재하지 않는다)
    DELETE FROM migration_work_key WHERE run_id = p_run_id;

    UPDATE migration_run
       SET error_count = o_error_count,
           info_count = o_info_count
     WHERE run_id = p_run_id;
END$$

DELIMITER ;
