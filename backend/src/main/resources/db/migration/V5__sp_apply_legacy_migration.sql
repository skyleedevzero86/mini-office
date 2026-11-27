-- =====================================================================================
-- Flyway 마이그레이션: 레거시 → 신규 적재 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V5
-- 작성일: 2026-11-22
-- 설명: migration_work_key의 레거시 행을 작품·현지화·지역·국가·장르·인물·출연·시즌·에피소드·외부 ID로 나눠 넣는다.
--       원본 키 유니크 제약 + ON DUPLICATE KEY UPDATE로 같은 행을 다시 돌려도 중복이 생기지 않는다 (백필과 동기화가 같은 프로시저를 쓴다).
--       하위 목록(장르·국가·출연)은 대상 작품 기준으로 지우고 다시 넣어 레거시에서 빠진 값도 따라간다.
--       외부 ID는 덮어쓰지 않는다: 이미 다른 값이 붙어 있으면 그대로 두고 운영자 확인으로 넘긴다.
--       운영자가 고친 현지화(source=MANUAL)도 레거시 값으로 덮지 않는다
-- =====================================================================================

DELIMITER $$

-- =====================================================================================
-- 1. 처리 대상 키 반영 (sp_upsert_legacy_keys)
-- 설명: ERROR 행은 건너뛴다. 커밋·롤백은 호출한 쪽(sp_backfill_legacy_chunk, sp_sync_legacy_changes)이 맡는다
-- =====================================================================================
CREATE PROCEDURE sp_upsert_legacy_keys(
    IN  p_run_id   BIGINT,                                            -- 이관 작업 ID
    OUT o_titles   INT,                                               -- 반환: 반영한 작품 수
    OUT o_credits  INT,                                               -- 반환: 대상 작품의 출연 수
    OUT o_episodes INT                                                -- 반환: 대상 작품의 에피소드 수
)
BEGIN
    -- ---------------------------------------------------------------------------------
    -- 1-1. 영화 (movies → content_title)
    -- ---------------------------------------------------------------------------------
    INSERT INTO content_title (title_type, original_title, original_language, release_date, runtime_min, status,
                               is_animation, vote_average, poster_path, origin_system, origin_key, migration_run_id)
    SELECT * FROM (
        SELECT 'MOVIE' AS title_type, TRIM(m.movie_title) AS original_title,
               IF(fn_map_code('COUNTRY_KO', m.movie_country) = 'KR', 'ko', NULL) AS original_language,
               fn_try_date(m.movie_open_date) AS release_date, fn_try_runtime(m.movie_runtime) AS runtime_min,
               IF(fn_try_date(m.movie_open_date) > CURDATE(), 'PLANNED', 'RELEASED') AS status,
               0 AS is_animation, m.movie_rating AS vote_average, NULLIF(TRIM(m.movie_img), '') AS poster_path,
               'LEGACY_MOVIES' AS origin_system, CAST(m.movie_seq AS CHAR) AS origin_key, p_run_id AS migration_run_id
          FROM migration_work_key w
          JOIN movies m ON m.movie_seq = w.source_seq
         WHERE w.run_id = p_run_id AND w.source_table = 'movies'
           AND NOT fn_row_has_error(p_run_id, 'movies', m.movie_seq)
    ) s
    ON DUPLICATE KEY UPDATE
        -- 내용이 바뀐 경우에만 메타 버전을 올린다 (먼저 계산해야 이전 값과 비교된다)
        -- 포스터는 레거시 값이 비어 있으면 기존 값(TMDB 반영분)을 지우지 않는다
        meta_version = content_title.meta_version + IF(content_title.original_title <=> s.original_title
                                                       AND content_title.release_date <=> s.release_date
                                                       AND content_title.runtime_min <=> s.runtime_min
                                                       AND content_title.vote_average <=> s.vote_average
                                                       AND content_title.poster_path <=> COALESCE(s.poster_path, content_title.poster_path)
                                                       AND content_title.status = s.status, 0, 1),
        original_title = s.original_title, original_language = s.original_language, release_date = s.release_date,
        runtime_min = s.runtime_min, status = s.status, vote_average = s.vote_average, poster_path = COALESCE(s.poster_path, content_title.poster_path),
        migration_run_id = s.migration_run_id;

    -- ---------------------------------------------------------------------------------
    -- 1-2. TV (MergedTVShow → content_title)
    -- ---------------------------------------------------------------------------------
    INSERT INTO content_title (title_type, original_title, original_language, release_date, status, is_animation,
                               popularity, vote_average, vote_count, poster_path, origin_system, origin_key, migration_run_id)
    SELECT * FROM (
        SELECT 'SERIES' AS title_type, COALESCE(NULLIF(TRIM(t.originalName), ''), TRIM(t.tvName)) AS original_title,
               NULLIF(LOWER(TRIM(t.originalLanguage)), '') AS original_language, fn_try_date(t.first_air_date) AS release_date,
               fn_map_code('TV_STATUS', t.tvStatus) AS status, 0 AS is_animation, t.popularity AS popularity,
               t.vote_average AS vote_average, t.vote_count AS vote_count, NULLIF(TRIM(t.poster_path), '') AS poster_path,
               'LEGACY_TV' AS origin_system, CAST(t.mgSeq AS CHAR) AS origin_key, p_run_id AS migration_run_id
          FROM migration_work_key w
          JOIN MergedTVShow t ON t.mgSeq = w.source_seq
         WHERE w.run_id = p_run_id AND w.source_table = 'MergedTVShow'
           AND NOT fn_row_has_error(p_run_id, 'MergedTVShow', t.mgSeq)
    ) s
    ON DUPLICATE KEY UPDATE
        meta_version = content_title.meta_version + IF(content_title.original_title <=> s.original_title
                                                       AND content_title.release_date <=> s.release_date
                                                       AND content_title.status = s.status
                                                       AND content_title.poster_path <=> COALESCE(s.poster_path, content_title.poster_path), 0, 1),
        original_title = s.original_title, original_language = s.original_language, release_date = s.release_date,
        status = s.status, popularity = s.popularity, vote_average = s.vote_average, vote_count = s.vote_count,
        poster_path = COALESCE(s.poster_path, content_title.poster_path), migration_run_id = s.migration_run_id;

    -- ---------------------------------------------------------------------------------
    -- 1-3. 애니 (cartoon_video → content_title, 애니메이션 시리즈)
    -- ---------------------------------------------------------------------------------
    INSERT INTO content_title (title_type, original_title, release_date, status, is_animation, vote_average,
                               poster_path, origin_system, origin_key, migration_run_id)
    SELECT * FROM (
        SELECT 'SERIES' AS title_type, COALESCE(NULLIF(TRIM(c.englishTitle), ''), TRIM(c.cartoonTitle)) AS original_title,
               fn_try_date(c.airing_date) AS release_date, fn_map_code('CARTOON_STATUS', c.status) AS status,
               1 AS is_animation, IF(TRIM(c.voteAverage) REGEXP '^[0-9]+(\\.[0-9])?$', CAST(TRIM(c.voteAverage) AS DECIMAL(3,1)), NULL) AS vote_average,
               NULLIF(TRIM(c.posterPath), '') AS poster_path,
               'LEGACY_CARTOON' AS origin_system, CAST(c.ctSeq AS CHAR) AS origin_key, p_run_id AS migration_run_id
          FROM migration_work_key w
          JOIN cartoon_video c ON c.ctSeq = w.source_seq
         WHERE w.run_id = p_run_id AND w.source_table = 'cartoon_video'
           AND NOT fn_row_has_error(p_run_id, 'cartoon_video', c.ctSeq)
    ) s
    ON DUPLICATE KEY UPDATE
        meta_version = content_title.meta_version + IF(content_title.original_title <=> s.original_title
                                                       AND content_title.release_date <=> s.release_date
                                                       AND content_title.status = s.status, 0, 1),
        original_title = s.original_title, release_date = s.release_date, status = s.status,
        vote_average = s.vote_average, poster_path = COALESCE(s.poster_path, content_title.poster_path), migration_run_id = s.migration_run_id;

    -- 이번에 반영한 작품 목록 (레거시 테이블 + 순번 → title_id)
    DROP TEMPORARY TABLE IF EXISTS tmp_work_title;
    CREATE TEMPORARY TABLE tmp_work_title (
        title_id     BIGINT      NOT NULL PRIMARY KEY,
        source_table VARCHAR(30) NOT NULL,
        source_seq   INT         NOT NULL
    );
    INSERT INTO tmp_work_title (title_id, source_table, source_seq)
    SELECT ct.title_id, w.source_table, w.source_seq
      FROM migration_work_key w
      JOIN content_title ct
        ON ct.origin_system = CASE w.source_table WHEN 'movies' THEN 'LEGACY_MOVIES'
                                                  WHEN 'MergedTVShow' THEN 'LEGACY_TV'
                                                  ELSE 'LEGACY_CARTOON' END
       AND ct.origin_key = CAST(w.source_seq AS CHAR)
     WHERE w.run_id = p_run_id
       AND NOT fn_row_has_error(p_run_id, w.source_table, w.source_seq);
    SELECT COUNT(*) INTO o_titles FROM tmp_work_title;

    -- ---------------------------------------------------------------------------------
    -- 2. 언어별 현지화
    -- ---------------------------------------------------------------------------------
    -- 한국어 제목·줄거리 (세 원본 모두)
    INSERT INTO content_localization (title_id, lang_code, local_title, overview, source)
    SELECT * FROM (
        SELECT x.title_id, 'ko' AS lang_code,
               CASE x.source_table WHEN 'movies' THEN TRIM(m.movie_title)
                                   WHEN 'MergedTVShow' THEN TRIM(t.tvName)
                                   ELSE TRIM(c.cartoonTitle) END AS local_title,
               CASE x.source_table WHEN 'movies' THEN m.movie_content
                                   WHEN 'MergedTVShow' THEN t.overview
                                   ELSE c.synopsis END AS overview,
               'LEGACY' AS source
          FROM tmp_work_title x
          LEFT JOIN movies m ON x.source_table = 'movies' AND m.movie_seq = x.source_seq
          LEFT JOIN MergedTVShow t ON x.source_table = 'MergedTVShow' AND t.mgSeq = x.source_seq
          LEFT JOIN cartoon_video c ON x.source_table = 'cartoon_video' AND c.ctSeq = x.source_seq
    ) s
    -- 운영자가 고친 현지화(source=MANUAL)는 레거시 값으로 덮지 않는다
    ON DUPLICATE KEY UPDATE local_title = IF(content_localization.source = 'MANUAL', content_localization.local_title, s.local_title),
                            overview = IF(content_localization.source = 'MANUAL', content_localization.overview, s.overview),
                            source = IF(content_localization.source = 'MANUAL', 'MANUAL', s.source);

    -- TV 원어 제목 (원어가 한국어가 아닐 때)
    INSERT INTO content_localization (title_id, lang_code, local_title, overview, source)
    SELECT * FROM (
        SELECT x.title_id, LOWER(TRIM(t.originalLanguage)) AS lang_code, TRIM(t.originalName) AS local_title,
               NULL AS overview, 'LEGACY' AS source
          FROM tmp_work_title x
          JOIN MergedTVShow t ON x.source_table = 'MergedTVShow' AND t.mgSeq = x.source_seq
         WHERE LOWER(TRIM(t.originalLanguage)) REGEXP '^[a-z]{2}$'
           AND LOWER(TRIM(t.originalLanguage)) <> 'ko'
           AND NULLIF(TRIM(t.originalName), '') IS NOT NULL
    ) s
    ON DUPLICATE KEY UPDATE local_title = IF(content_localization.source = 'MANUAL', content_localization.local_title, s.local_title),
                            source = IF(content_localization.source = 'MANUAL', 'MANUAL', s.source);

    -- 애니 영어 제목
    INSERT INTO content_localization (title_id, lang_code, local_title, overview, source)
    SELECT * FROM (
        SELECT x.title_id, 'en' AS lang_code, TRIM(c.englishTitle) AS local_title, NULL AS overview, 'LEGACY' AS source
          FROM tmp_work_title x
          JOIN cartoon_video c ON x.source_table = 'cartoon_video' AND c.ctSeq = x.source_seq
         WHERE NULLIF(TRIM(c.englishTitle), '') IS NOT NULL
    ) s
    ON DUPLICATE KEY UPDATE local_title = IF(content_localization.source = 'MANUAL', content_localization.local_title, s.local_title),
                            source = IF(content_localization.source = 'MANUAL', 'MANUAL', s.source);

    -- ---------------------------------------------------------------------------------
    -- 3. 지역별 공개 정보 (레거시는 한국 서비스라 KR 공개일·등급으로 넣는다)
    -- ---------------------------------------------------------------------------------
    INSERT INTO content_title_region (title_id, region_code, release_date, age_rating, source)
    SELECT * FROM (
        SELECT x.title_id, 'KR' AS region_code, fn_try_date(m.movie_open_date) AS release_date,
               COALESCE(fn_map_code('AGE_RATING', m.movie_age), NULLIF(TRIM(m.movie_age), '')) AS age_rating, 'LEGACY' AS source
          FROM tmp_work_title x
          JOIN movies m ON x.source_table = 'movies' AND m.movie_seq = x.source_seq
    ) s
    ON DUPLICATE KEY UPDATE release_date = s.release_date, age_rating = s.age_rating, source = s.source;

    -- ---------------------------------------------------------------------------------
    -- 4. 제작 국가 (지우고 다시 넣기)
    -- ---------------------------------------------------------------------------------
    DELETE cc FROM content_title_country cc JOIN tmp_work_title x ON x.title_id = cc.title_id;

    INSERT IGNORE INTO content_title_country (title_id, country_code)
    SELECT x.title_id, fn_map_code('COUNTRY_KO', m.movie_country)
      FROM tmp_work_title x
      JOIN movies m ON x.source_table = 'movies' AND m.movie_seq = x.source_seq
     WHERE fn_map_code('COUNTRY_KO', m.movie_country) IS NOT NULL;

    -- TV 제작 국가: 'KR', 'US,GB', '[''US'']' 형태를 모두 받는다
    INSERT IGNORE INTO content_title_country (title_id, country_code)
    SELECT x.title_id, UPPER(TRIM(j.code))
      FROM tmp_work_title x
      JOIN MergedTVShow t ON x.source_table = 'MergedTVShow' AND t.mgSeq = x.source_seq
      JOIN JSON_TABLE(CONCAT('["', REPLACE(REGEXP_REPLACE(COALESCE(t.originCountry, ''), '[^A-Za-z,]', ''), ',', '","'), '"]'),
                      '$[*]' COLUMNS (code VARCHAR(10) PATH '$')) j
     WHERE UPPER(TRIM(j.code)) REGEXP '^[A-Z]{2}$';

    -- ---------------------------------------------------------------------------------
    -- 5. 장르 (지우고 다시 넣기)
    -- ---------------------------------------------------------------------------------
    DELETE tg FROM content_title_genre tg JOIN tmp_work_title x ON x.title_id = tg.title_id;

    -- 영화·애니: 쉼표로 나뉜 한국어 장르 이름
    INSERT IGNORE INTO content_title_genre (title_id, genre_code)
    SELECT x.title_id, fn_map_code('GENRE_KO', j.name)
      FROM tmp_work_title x
      LEFT JOIN movies m ON x.source_table = 'movies' AND m.movie_seq = x.source_seq
      LEFT JOIN cartoon_video c ON x.source_table = 'cartoon_video' AND c.ctSeq = x.source_seq
      JOIN JSON_TABLE(CONCAT('["', REPLACE(REPLACE(REPLACE(COALESCE(m.movie_type, c.genre, ''), '\\', ''), '"', ''), ',', '","'), '"]'),
                      '$[*]' COLUMNS (name VARCHAR(50) PATH '$')) j
     WHERE x.source_table IN ('movies', 'cartoon_video')
       AND fn_map_code('GENRE_KO', j.name) IS NOT NULL;

    -- 애니는 항상 애니메이션 장르
    INSERT IGNORE INTO content_title_genre (title_id, genre_code)
    SELECT x.title_id, 'ANIMATION' FROM tmp_work_title x WHERE x.source_table = 'cartoon_video';

    -- TV: TMDB TV 장르 ID 목록
    INSERT IGNORE INTO content_title_genre (title_id, genre_code)
    SELECT x.title_id, g.genre_code
      FROM tmp_work_title x
      JOIN MergedTVShow t ON x.source_table = 'MergedTVShow' AND t.mgSeq = x.source_seq
      JOIN JSON_TABLE(CONCAT('[', REGEXP_REPLACE(COALESCE(t.genreIds, ''), '[^0-9,]', ''), ']'),
                      '$[*]' COLUMNS (tmdb_id INT PATH '$' NULL ON ERROR)) j
      JOIN content_genre g ON g.tmdb_tv_id = j.tmdb_id;

    -- ---------------------------------------------------------------------------------
    -- 6. 인물·출연 (영화의 배우·감독 문자열을 나눠 넣는다. 이름만 있으므로 name_only=1)
    -- ---------------------------------------------------------------------------------
    DROP TEMPORARY TABLE IF EXISTS tmp_work_credit;
    CREATE TEMPORARY TABLE tmp_work_credit (
        title_id     BIGINT       NOT NULL,
        person_name  VARCHAR(200) NOT NULL,
        role_type    VARCHAR(10)  NOT NULL,
        credit_order SMALLINT     NOT NULL
    );
    INSERT INTO tmp_work_credit (title_id, person_name, role_type, credit_order)
    SELECT x.title_id, TRIM(j.name), 'CAST', j.ord
      FROM tmp_work_title x
      JOIN movies m ON x.source_table = 'movies' AND m.movie_seq = x.source_seq
      JOIN JSON_TABLE(CONCAT('["', REPLACE(REPLACE(REPLACE(COALESCE(m.movie_actor, ''), '\\', ''), '"', ''), ',', '","'), '"]'),
                      '$[*]' COLUMNS (ord FOR ORDINALITY, name VARCHAR(200) PATH '$')) j
     WHERE NULLIF(TRIM(j.name), '') IS NOT NULL;

    -- 임시 테이블은 한 문장에서 두 번 열 수 없어서 감독은 따로 넣는다
    INSERT INTO tmp_work_credit (title_id, person_name, role_type, credit_order)
    SELECT x.title_id, TRIM(j.name), 'DIRECTOR', j.ord
      FROM tmp_work_title x
      JOIN movies m ON x.source_table = 'movies' AND m.movie_seq = x.source_seq
      JOIN JSON_TABLE(CONCAT('["', REPLACE(REPLACE(REPLACE(COALESCE(m.movie_director, ''), '\\', ''), '"', ''), ',', '","'), '"]'),
                      '$[*]' COLUMNS (ord FOR ORDINALITY, name VARCHAR(200) PATH '$')) j
     WHERE NULLIF(TRIM(j.name), '') IS NOT NULL;

    INSERT INTO content_person (person_name, name_only, origin_system, origin_key)
    SELECT DISTINCT person_name, 1, 'LEGACY_NAME', person_name FROM tmp_work_credit
    ON DUPLICATE KEY UPDATE person_name = content_person.person_name;

    DELETE cr FROM content_credit cr JOIN tmp_work_title x ON x.title_id = cr.title_id;

    INSERT IGNORE INTO content_credit (title_id, person_id, role_type, character_name, credit_order)
    SELECT c.title_id, p.person_id, c.role_type, '', c.credit_order
      FROM tmp_work_credit c
      JOIN content_person p ON p.origin_system = 'LEGACY_NAME' AND p.origin_key = c.person_name;

    SELECT COUNT(*) INTO o_credits FROM content_credit cr JOIN tmp_work_title x ON x.title_id = cr.title_id;

    -- ---------------------------------------------------------------------------------
    -- 7. 시즌·에피소드 (애니의 화수 범위로 시즌 1과 회차를 만든다)
    -- ---------------------------------------------------------------------------------
    INSERT INTO content_season (title_id, season_no, season_name, air_date, episode_count)
    SELECT * FROM (
        SELECT x.title_id, 1 AS season_no, '시즌 1' AS season_name, fn_try_date(c.airing_date) AS air_date,
               CAST(TRIM(c.episode_end) AS UNSIGNED) - CAST(TRIM(c.episode_start) AS UNSIGNED) + 1 AS episode_count
          FROM tmp_work_title x
          JOIN cartoon_video c ON x.source_table = 'cartoon_video' AND c.ctSeq = x.source_seq
         WHERE TRIM(c.episode_start) REGEXP '^[0-9]{1,4}$'
           AND TRIM(c.episode_end) REGEXP '^[0-9]{1,4}$'
           AND CAST(TRIM(c.episode_start) AS UNSIGNED) BETWEEN 1 AND CAST(TRIM(c.episode_end) AS UNSIGNED)
    ) s
    ON DUPLICATE KEY UPDATE air_date = s.air_date, episode_count = s.episode_count;

    INSERT IGNORE INTO content_episode (season_id, episode_no)
    WITH RECURSIVE ep (season_id, episode_no, episode_end) AS (
        SELECT cs.season_id, CAST(TRIM(c.episode_start) AS UNSIGNED), CAST(TRIM(c.episode_end) AS UNSIGNED)
          FROM tmp_work_title x
          JOIN cartoon_video c ON x.source_table = 'cartoon_video' AND c.ctSeq = x.source_seq
          JOIN content_season cs ON cs.title_id = x.title_id AND cs.season_no = 1
         WHERE TRIM(c.episode_start) REGEXP '^[0-9]{1,4}$'
           AND TRIM(c.episode_end) REGEXP '^[0-9]{1,4}$'
           AND CAST(TRIM(c.episode_start) AS UNSIGNED) BETWEEN 1 AND CAST(TRIM(c.episode_end) AS UNSIGNED)
        UNION ALL
        SELECT season_id, episode_no + 1, episode_end FROM ep WHERE episode_no < episode_end
    )
    SELECT season_id, episode_no FROM ep;

    -- 화수가 줄었으면 범위 밖 회차를 지운다
    DELETE e FROM content_episode e
      JOIN content_season cs ON cs.season_id = e.season_id AND cs.season_no = 1
      JOIN tmp_work_title x ON x.title_id = cs.title_id AND x.source_table = 'cartoon_video'
      JOIN cartoon_video c ON c.ctSeq = x.source_seq
     WHERE e.episode_no < CAST(TRIM(c.episode_start) AS UNSIGNED)
        OR e.episode_no > CAST(TRIM(c.episode_end) AS UNSIGNED);

    SELECT COUNT(*) INTO o_episodes
      FROM content_episode e
      JOIN content_season cs ON cs.season_id = e.season_id
      JOIN tmp_work_title x ON x.title_id = cs.title_id;

    -- ---------------------------------------------------------------------------------
    -- 8. 외부 표준 ID (TV의 TMDB TV ID). 이미 다른 값이 붙은 대상·다른 대상이 가진 값은 건드리지 않는다
    -- ---------------------------------------------------------------------------------
    INSERT INTO content_external_id (entity_type, entity_id, provider, external_id, match_method)
    SELECT 'TITLE', x.title_id, 'TMDB_TV', TRIM(t.tvNumber), 'LEGACY'
      FROM tmp_work_title x
      JOIN MergedTVShow t ON x.source_table = 'MergedTVShow' AND t.mgSeq = x.source_seq
     WHERE TRIM(t.tvNumber) REGEXP '^[0-9]+$'
       AND NOT EXISTS (SELECT 1 FROM content_external_id e
                        WHERE e.provider = 'TMDB_TV'
                          AND (e.external_id = TRIM(t.tvNumber) OR (e.entity_type = 'TITLE' AND e.entity_id = x.title_id)));

    DROP TEMPORARY TABLE IF EXISTS tmp_work_credit;
    DROP TEMPORARY TABLE IF EXISTS tmp_work_title;
END$$

-- =====================================================================================
-- 2. 백필 한 묶음 (sp_backfill_legacy_chunk)
-- 설명: 체크포인트 다음 원본 행을 테이블마다 batch_size개씩 꺼내 검증 → 반영하고 체크포인트를 옮긴다.
--       묶음마다 커밋해서 잠금을 짧게 잡는다. 실패하면 이 묶음만 롤백하고 작업을 FAILED로 남긴 뒤 오류를 다시 던진다.
--       다시 호출하면 체크포인트에서 이어 간다 (이미 반영한 행은 업서트라 중복이 생기지 않는다)
-- =====================================================================================
CREATE PROCEDURE sp_backfill_legacy_chunk(
    IN  p_run_id   BIGINT,                                            -- 이관 작업 ID (mode=BACKFILL)
    OUT o_done     TINYINT,                                           -- 반환: 남은 원본 행이 없으면 1
    OUT o_titles   INT,                                               -- 반환: 이번 묶음에서 반영한 작품 수
    OUT o_errors   INT                                                -- 반환: 이번 묶음의 검증 오류(ERROR) 건수
)
BEGIN
    DECLARE v_mode      VARCHAR(10);
    DECLARE v_status    VARCHAR(30);
    DECLARE v_batch     INT;
    DECLARE v_keys      INT;
    DECLARE v_infos     INT;
    DECLARE v_credits   INT;
    DECLARE v_episodes  INT;
    DECLARE v_message   TEXT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_message = MESSAGE_TEXT;
        ROLLBACK;
        UPDATE migration_run
           SET status = 'FAILED', error_message = LEFT(v_message, 2000), finished_at = CURRENT_TIMESTAMP(3)
         WHERE run_id = p_run_id;
        COMMIT;
        RESIGNAL;
    END;

    SELECT mode, status, batch_size INTO v_mode, v_status, v_batch FROM migration_run WHERE run_id = p_run_id;
    IF v_mode IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '이관 작업을 찾을 수 없습니다.';
    END IF;
    IF v_mode <> 'BACKFILL' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '백필 작업이 아닙니다.';
    END IF;

    -- 처음 실행이거나 실패 뒤 다시 실행하면 실행 횟수를 올린다 (묶음 트랜잭션 밖에서 커밋해서 이 묶음이 실패해도 횟수는 남긴다).
    -- 처음 실행이면 검증 건수를 0부터 센다 (같은 작업으로 사전 검증을 먼저 했어도 묶음 검증과 두 번 더하지 않는다)
    IF v_status IN ('CREATED', 'FAILED') THEN
        START TRANSACTION;
        UPDATE migration_run
           SET error_count = IF(status = 'CREATED', 0, error_count),
               info_count = IF(status = 'CREATED', 0, info_count),
               status = 'RUNNING', attempt_count = attempt_count + 1, started_at = CURRENT_TIMESTAMP(3), error_message = NULL
         WHERE run_id = p_run_id;
        COMMIT;
    END IF;

    START TRANSACTION;

    -- 다음 묶음 키 담기 (테이블마다 체크포인트 다음부터 batch_size개)
    DELETE FROM migration_work_key WHERE run_id = p_run_id;
    INSERT INTO migration_work_key (run_id, source_table, source_seq)
    SELECT p_run_id, 'movies', m.movie_seq
      FROM movies m, migration_run r
     WHERE r.run_id = p_run_id AND m.movie_seq > r.last_movie_seq
     ORDER BY m.movie_seq
     LIMIT v_batch;
    INSERT INTO migration_work_key (run_id, source_table, source_seq)
    SELECT p_run_id, 'MergedTVShow', t.mgSeq
      FROM MergedTVShow t, migration_run r
     WHERE r.run_id = p_run_id AND t.mgSeq > r.last_tv_seq
     ORDER BY t.mgSeq
     LIMIT v_batch;
    INSERT INTO migration_work_key (run_id, source_table, source_seq)
    SELECT p_run_id, 'cartoon_video', c.ctSeq
      FROM cartoon_video c, migration_run r
     WHERE r.run_id = p_run_id AND c.ctSeq > r.last_cartoon_seq
     ORDER BY c.ctSeq
     LIMIT v_batch;

    SELECT COUNT(*) INTO v_keys FROM migration_work_key WHERE run_id = p_run_id;

    IF v_keys = 0 THEN
        -- 남은 행 없음: 작업 마감
        UPDATE migration_run
           SET status = IF(error_count > 0, 'COMPLETED_WITH_ISSUES', 'COMPLETED'), finished_at = CURRENT_TIMESTAMP(3)
         WHERE run_id = p_run_id;
        SET o_done = 1, o_titles = 0, o_errors = 0;
    ELSE
        CALL sp_validate_legacy_keys(p_run_id, o_errors, v_infos);
        CALL sp_upsert_legacy_keys(p_run_id, o_titles, v_credits, v_episodes);

        -- 체크포인트 이동과 누적 건수
        UPDATE migration_run r
           SET r.last_movie_seq    = GREATEST(r.last_movie_seq, COALESCE((SELECT MAX(source_seq) FROM migration_work_key WHERE run_id = p_run_id AND source_table = 'movies'), 0)),
               r.last_tv_seq       = GREATEST(r.last_tv_seq, COALESCE((SELECT MAX(source_seq) FROM migration_work_key WHERE run_id = p_run_id AND source_table = 'MergedTVShow'), 0)),
               r.last_cartoon_seq  = GREATEST(r.last_cartoon_seq, COALESCE((SELECT MAX(source_seq) FROM migration_work_key WHERE run_id = p_run_id AND source_table = 'cartoon_video'), 0)),
               r.batch_count       = r.batch_count + 1,
               r.error_count       = r.error_count + o_errors,
               r.info_count        = r.info_count + v_infos,
               r.upserted_titles   = r.upserted_titles + o_titles,
               r.upserted_credits  = r.upserted_credits + v_credits,
               r.upserted_episodes = r.upserted_episodes + v_episodes
         WHERE r.run_id = p_run_id;
        SET o_done = 0;
    END IF;

    DELETE FROM migration_work_key WHERE run_id = p_run_id;
    COMMIT;
END$$

DELIMITER ;
