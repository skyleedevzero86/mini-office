-- =====================================================================================
-- Flyway 마이그레이션: 레거시 변경분 기록 트리거와 동기화 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V6
-- 작성일: 2026-11-22
-- 설명: 레거시 화면은 그대로 movies·MergedTVShow·cartoon_video에 쓰고, 트리거가 변경된 키만 legacy_change_log에 남긴다.
--       동기화 프로시저가 쌓인 키를 묶음으로 꺼내 V5 적재 로직으로 신규 테이블에 반영한다.
--       트리거는 키만 남기고 내용을 복사하지 않아서 레거시 쓰기 지연이 거의 늘지 않는다.
--       백필을 시작하기 전에 이 파일이 먼저 적용되어 있어야 백필 도중의 변경도 놓치지 않는다
-- =====================================================================================

DELIMITER $$

-- =====================================================================================
-- 1. 변경분 기록 트리거 (영화)
-- =====================================================================================
CREATE TRIGGER trg_movies_ai AFTER INSERT ON movies FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('movies', NEW.movie_seq, 'I');
END$$

CREATE TRIGGER trg_movies_au AFTER UPDATE ON movies FOR EACH ROW
BEGIN
    -- 조회수(movie_cnt)만 바뀐 경우는 메타가 아니므로 남기지 않는다
    IF NOT (OLD.movie_title <=> NEW.movie_title AND OLD.movie_content <=> NEW.movie_content
            AND OLD.movie_img <=> NEW.movie_img AND OLD.movie_type <=> NEW.movie_type
            AND OLD.movie_open_date <=> NEW.movie_open_date AND OLD.movie_rating <=> NEW.movie_rating
            AND OLD.movie_runtime <=> NEW.movie_runtime AND OLD.movie_actor <=> NEW.movie_actor
            AND OLD.movie_country <=> NEW.movie_country AND OLD.movie_director <=> NEW.movie_director
            AND OLD.movie_age <=> NEW.movie_age) THEN
        INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('movies', NEW.movie_seq, 'U');
    END IF;
END$$

CREATE TRIGGER trg_movies_ad AFTER DELETE ON movies FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('movies', OLD.movie_seq, 'D');
END$$

-- =====================================================================================
-- 2. 변경분 기록 트리거 (TV)
-- =====================================================================================
CREATE TRIGGER trg_tvshow_ai AFTER INSERT ON MergedTVShow FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('MergedTVShow', NEW.mgSeq, 'I');
END$$

CREATE TRIGGER trg_tvshow_au AFTER UPDATE ON MergedTVShow FOR EACH ROW
BEGIN
    -- 상세 조회 때마다 vote_count를 1 올리는 레거시 동작(updateHit)은 메타 변경이 아니므로 남기지 않는다
    IF NOT (OLD.tvNumber <=> NEW.tvNumber AND OLD.tvName <=> NEW.tvName AND OLD.overview <=> NEW.overview
            AND OLD.first_air_date <=> NEW.first_air_date AND OLD.poster_path <=> NEW.poster_path
            AND OLD.genreIds <=> NEW.genreIds AND OLD.originCountry <=> NEW.originCountry
            AND OLD.originalLanguage <=> NEW.originalLanguage AND OLD.originalName <=> NEW.originalName
            AND OLD.tvStatus <=> NEW.tvStatus) THEN
        INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('MergedTVShow', NEW.mgSeq, 'U');
    END IF;
END$$

CREATE TRIGGER trg_tvshow_ad AFTER DELETE ON MergedTVShow FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('MergedTVShow', OLD.mgSeq, 'D');
END$$

-- =====================================================================================
-- 3. 변경분 기록 트리거 (애니)
-- =====================================================================================
CREATE TRIGGER trg_cartoon_ai AFTER INSERT ON cartoon_video FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('cartoon_video', NEW.ctSeq, 'I');
END$$

CREATE TRIGGER trg_cartoon_au AFTER UPDATE ON cartoon_video FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('cartoon_video', NEW.ctSeq, 'U');
END$$

CREATE TRIGGER trg_cartoon_ad AFTER DELETE ON cartoon_video FOR EACH ROW
BEGIN
    INSERT INTO legacy_change_log (source_table, source_seq, operation) VALUES ('cartoon_video', OLD.ctSeq, 'D');
END$$

-- =====================================================================================
-- 4. 변경분 동기화 (sp_sync_legacy_changes)
-- 설명: 아직 반영하지 않은 변경을 change_id 순서로 p_batch_size개 꺼낸다 (SKIP LOCKED라 동시에 두 개가 돌아도 겹치지 않는다).
--       같은 키가 여러 번 바뀌었어도 지금 원본 상태 하나로 반영한다: 원본 행이 있으면 업서트, 없으면 작품을 DELETED로 바꾼다.
--       묶음마다 커밋한다. 실패하면 롤백하고 작업을 FAILED로 남긴 뒤 오류를 다시 던진다 (다음 호출에서 같은 변경을 다시 집는다)
-- =====================================================================================
CREATE PROCEDURE sp_sync_legacy_changes(
    IN  p_run_id     BIGINT,                                          -- 동기화 작업 ID (mode=SYNC)
    IN  p_batch_size INT,                                             -- 한 번에 꺼낼 변경 수
    OUT o_changes    INT,                                             -- 반환: 반영 처리한 변경 수
    OUT o_titles     INT,                                             -- 반환: 업서트한 작품 수
    OUT o_deleted    INT,                                             -- 반환: DELETED로 바꾼 작품 수
    OUT o_errors     INT                                              -- 반환: 검증 오류(ERROR)로 건너뛴 원본 행 수
)
BEGIN
    DECLARE v_mode     VARCHAR(10);
    DECLARE v_infos    INT;
    DECLARE v_credits  INT;
    DECLARE v_episodes INT;
    DECLARE v_message  TEXT;

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

    SELECT mode INTO v_mode FROM migration_run WHERE run_id = p_run_id;
    IF v_mode IS NULL OR v_mode <> 'SYNC' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '동기화 작업이 아니거나 찾을 수 없습니다.';
    END IF;

    -- 상태·실행 횟수는 묶음 트랜잭션 밖에서 커밋한다 (이 묶음이 실패해도 횟수는 남긴다)
    START TRANSACTION;
    UPDATE migration_run
       SET attempt_count = attempt_count + IF(status IN ('CREATED', 'FAILED'), 1, 0),
           status = 'RUNNING', started_at = COALESCE(started_at, CURRENT_TIMESTAMP(3)), error_message = NULL
     WHERE run_id = p_run_id;
    COMMIT;

    START TRANSACTION;

    -- 이번에 처리할 변경 (다른 세션이 잡고 있는 행은 건너뛴다)
    DROP TEMPORARY TABLE IF EXISTS tmp_sync_change;
    CREATE TEMPORARY TABLE tmp_sync_change (change_id BIGINT NOT NULL PRIMARY KEY);
    INSERT INTO tmp_sync_change (change_id)
    SELECT change_id
      FROM legacy_change_log
     WHERE applied_run_id IS NULL
     ORDER BY change_id
     LIMIT p_batch_size
       FOR UPDATE SKIP LOCKED;
    SELECT COUNT(*) INTO o_changes FROM tmp_sync_change;

    SET o_titles = 0, o_deleted = 0, o_errors = 0;

    IF o_changes > 0 THEN
        -- 바뀐 키 중 원본 행이 남아 있는 것만 업서트 대상으로 담는다
        DELETE FROM migration_work_key WHERE run_id = p_run_id;
        INSERT INTO migration_work_key (run_id, source_table, source_seq)
        SELECT DISTINCT p_run_id, l.source_table, l.source_seq
          FROM legacy_change_log l
          JOIN tmp_sync_change t ON t.change_id = l.change_id
         WHERE (l.source_table = 'movies' AND EXISTS (SELECT 1 FROM movies m WHERE m.movie_seq = l.source_seq))
            OR (l.source_table = 'MergedTVShow' AND EXISTS (SELECT 1 FROM MergedTVShow s WHERE s.mgSeq = l.source_seq))
            OR (l.source_table = 'cartoon_video' AND EXISTS (SELECT 1 FROM cartoon_video c WHERE c.ctSeq = l.source_seq));

        IF EXISTS (SELECT 1 FROM migration_work_key WHERE run_id = p_run_id) THEN
            CALL sp_validate_legacy_keys(p_run_id, o_errors, v_infos);
            CALL sp_upsert_legacy_keys(p_run_id, o_titles, v_credits, v_episodes);
        ELSE
            SET v_infos = 0, v_credits = 0, v_episodes = 0;
        END IF;

        -- 원본 행이 없어진 키는 신규 작품을 DELETED로 바꾼다 (자식 데이터는 남겨 되돌릴 수 있게 한다)
        UPDATE content_title ct
          JOIN (SELECT DISTINCT l.source_table, l.source_seq
                  FROM legacy_change_log l
                  JOIN tmp_sync_change t ON t.change_id = l.change_id) d
            ON ct.origin_system = CASE d.source_table WHEN 'movies' THEN 'LEGACY_MOVIES'
                                                      WHEN 'MergedTVShow' THEN 'LEGACY_TV'
                                                      ELSE 'LEGACY_CARTOON' END
           AND ct.origin_key = CAST(d.source_seq AS CHAR)
           SET ct.status = 'DELETED', ct.meta_version = ct.meta_version + 1, ct.migration_run_id = p_run_id
         WHERE ct.status <> 'DELETED'
           AND NOT EXISTS (SELECT 1 FROM migration_work_key w
                            WHERE w.run_id = p_run_id AND w.source_table = d.source_table AND w.source_seq = d.source_seq);
        SET o_deleted = ROW_COUNT();

        -- 처리한 변경 표시
        UPDATE legacy_change_log l
          JOIN tmp_sync_change t ON t.change_id = l.change_id
           SET l.applied_run_id = p_run_id, l.applied_at = CURRENT_TIMESTAMP(3);

        UPDATE migration_run
           SET batch_count = batch_count + 1,
               error_count = error_count + o_errors,
               info_count = info_count + v_infos,
               upserted_titles = upserted_titles + o_titles,
               upserted_credits = upserted_credits + v_credits,
               upserted_episodes = upserted_episodes + v_episodes
         WHERE run_id = p_run_id;

        DELETE FROM migration_work_key WHERE run_id = p_run_id;
    END IF;

    UPDATE migration_run SET finished_at = CURRENT_TIMESTAMP(3) WHERE run_id = p_run_id;
    DROP TEMPORARY TABLE IF EXISTS tmp_sync_change;
    COMMIT;
END$$

DELIMITER ;
