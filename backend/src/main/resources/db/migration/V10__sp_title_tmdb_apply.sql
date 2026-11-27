-- =====================================================================================
-- Flyway 마이그레이션: TMDB 정보 반영 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V10
-- 작성일: 2026-11-22
-- 설명: 운영자가 TMDB 상세를 확인하고 "반영"을 누르면 포스터·IMDB ID·영어 제목/줄거리를 신규 메타에 넣는다.
--       대조 지문(원제·공개일·상영시간·상태)은 건드리지 않으므로 반영해도 원본·신규 대조가 깨지지 않는다.
--       자동 반영은 사람이 확인한 값(MANUAL)과 이미 있는 다른 연결을 덮지 않는다.
--       커밋·롤백은 호출한 서비스 트랜잭션이 맡는다
-- =====================================================================================

DELIMITER $$

-- =====================================================================================
-- 1. TMDB 정보 반영 (sp_title_tmdb_apply)
-- 설명: 포스터는 값이 다를 때만 바꾸고 메타 버전을 올린다.
--       IMDB ID는 작품에 IMDB 연결이 없고 다른 대상이 쓰지 않을 때만 연결한다 (match_method=API, verified=0).
--       영어 현지화는 없으면 만들고, TMDB가 만든 행이면 갱신하고, 레거시 행이면 비어 있는 줄거리만 채운다. MANUAL은 그대로 둔다.
--       out_note에 무엇을 반영하고 무엇을 건너뛰었는지 남긴다
-- =====================================================================================
CREATE PROCEDURE sp_title_tmdb_apply(
    IN    in_worker      VARCHAR(100),                                -- 작업자
    IN    in_workip      VARCHAR(50),                                 -- 작업 IP
    IN    in_title_id    BIGINT,                                      -- 작품 ID
    IN    in_poster_path VARCHAR(300),                                -- TMDB 포스터 경로 (/abc.jpg)
    IN    in_imdb_id     VARCHAR(50),                                 -- TMDB가 알려 준 IMDB ID (tt0000000)
    IN    in_en_title    VARCHAR(300),                                -- 영어 제목
    IN    in_en_overview TEXT,                                        -- 영어 줄거리
    OUT   out_note       VARCHAR(1000),                               -- 반환: 반영·건너뜀 내역
    INOUT rtn_cod        VARCHAR(1),                                  -- 반환: 1 성공, 0 실패
    INOUT err_msg        VARCHAR(1000)                                -- 반환: 실패 사유 (한국어)
)
proc: BEGIN
    DECLARE v_poster  VARCHAR(300) DEFAULT NULLIF(TRIM(in_poster_path), '');
    DECLARE v_imdb    VARCHAR(50)  DEFAULT NULLIF(TRIM(in_imdb_id), '');
    DECLARE v_title   VARCHAR(300) DEFAULT NULLIF(TRIM(in_en_title), '');
    DECLARE v_owner   VARCHAR(100);
    DECLARE v_source  VARCHAR(20);
    DECLARE v_changed INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 err_msg = MESSAGE_TEXT;
        SET rtn_cod = '0', err_msg = LEFT(CONCAT('DB 오류: ', err_msg), 1000);
    END;

    SET rtn_cod = '0', err_msg = '', out_note = '';

    IF NOT EXISTS (SELECT 1 FROM content_title WHERE title_id = in_title_id AND status <> 'DELETED') THEN
        SET err_msg = CONCAT('작품이 없거나 삭제되었습니다. (', COALESCE(in_title_id, '-'), ')'); LEAVE proc;
    END IF;
    IF v_poster IS NOT NULL AND v_poster NOT REGEXP '^/[A-Za-z0-9_.-]+$' THEN
        SET err_msg = CONCAT('포스터 경로 형식이 맞지 않습니다. (', v_poster, ')'); LEAVE proc;
    END IF;

    -- 1) 포스터
    IF v_poster IS NOT NULL THEN
        UPDATE content_title
           SET poster_path = v_poster, meta_version = meta_version + 1
         WHERE title_id = in_title_id AND NOT (poster_path <=> v_poster);
        SET v_changed = ROW_COUNT();
        SET out_note = CONCAT(out_note, IF(v_changed > 0, '포스터 반영', '포스터 같음'), '; ');
    END IF;

    -- 2) IMDB ID
    IF v_imdb IS NOT NULL THEN
        IF NOT fn_external_id_format_ok('IMDB', 'TITLE', v_imdb) THEN
            SET out_note = CONCAT(out_note, 'IMDB 형식 오류로 건너뜀(', v_imdb, '); ');
        ELSEIF EXISTS (SELECT 1 FROM content_external_id WHERE entity_type = 'TITLE' AND entity_id = in_title_id AND provider = 'IMDB') THEN
            SET out_note = CONCAT(out_note, 'IMDB 이미 연결됨; ');
        ELSE
            SET v_owner = (SELECT CONCAT(entity_type, ' ', entity_id) FROM content_external_id
                            WHERE provider = 'IMDB' AND external_id = v_imdb LIMIT 1);
            IF v_owner IS NOT NULL THEN
                SET out_note = CONCAT(out_note, 'IMDB ', v_imdb, '는 ', v_owner, '에 연결되어 있어 건너뜀; ');
            ELSE
                INSERT INTO content_external_id (entity_type, entity_id, provider, external_id, match_method, verified)
                VALUES ('TITLE', in_title_id, 'IMDB', v_imdb, 'API', 0);
                SET out_note = CONCAT(out_note, 'IMDB ', v_imdb, ' 연결; ');
            END IF;
        END IF;
    END IF;

    -- 3) 영어 현지화
    IF v_title IS NOT NULL THEN
        SET v_source = (SELECT source FROM content_localization WHERE title_id = in_title_id AND lang_code = 'en');
        IF v_source IS NULL THEN
            INSERT INTO content_localization (title_id, lang_code, local_title, overview, source)
            VALUES (in_title_id, 'en', v_title, NULLIF(TRIM(in_en_overview), ''), 'TMDB');
            SET out_note = CONCAT(out_note, '영어 현지화 등록; ');
        ELSEIF v_source = 'TMDB' THEN
            UPDATE content_localization
               SET local_title = v_title, overview = NULLIF(TRIM(in_en_overview), '')
             WHERE title_id = in_title_id AND lang_code = 'en';
            SET out_note = CONCAT(out_note, '영어 현지화 갱신; ');
        ELSEIF v_source = 'LEGACY' THEN
            UPDATE content_localization
               SET overview = NULLIF(TRIM(in_en_overview), '')
             WHERE title_id = in_title_id AND lang_code = 'en' AND overview IS NULL;
            SET out_note = CONCAT(out_note, IF(ROW_COUNT() > 0, '영어 줄거리 채움', '영어 현지화는 레거시 값 유지'), '; ');
        ELSE
            SET out_note = CONCAT(out_note, '영어 현지화는 운영자 값(MANUAL) 유지; ');
        END IF;
    END IF;

    CALL sp_write_work_log('sp_title_tmdb_apply', 'U', 'content_title', CAST(in_title_id AS CHAR), out_note, in_worker, in_workip);
    SET rtn_cod = '1';
END$$

DELIMITER ;
