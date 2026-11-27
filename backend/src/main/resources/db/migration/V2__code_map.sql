-- =====================================================================================
-- Flyway 마이그레이션: 코드 대응표와 장르 기준 데이터
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V2
-- 작성일: 2026-11-22
-- 설명: 레거시 값(한국어 나라 이름, 상태 코드, 한국어 장르 이름)을 새 표준 코드로 바꾸는 대응표를 만든다.
--       대응이 없는 값은 이관 검증에서 사유와 함께 남긴다
-- =====================================================================================

-- =====================================================================================
-- 1. 코드 대응표 (code_map)
-- 설명: 코드 그룹 + 레거시 값 → 새 코드
-- =====================================================================================
CREATE TABLE code_map (
    code_group     VARCHAR(30)  NOT NULL                                  COMMENT '코드 그룹 (TV_STATUS, CARTOON_STATUS, COUNTRY_KO, GENRE_KO, AGE_RATING)',
    legacy_code    VARCHAR(100) NOT NULL                                  COMMENT '레거시 값',
    new_code       VARCHAR(30)  NOT NULL                                  COMMENT '새 코드',
    note           VARCHAR(200)                                           COMMENT '비고',
    PRIMARY KEY (code_group, legacy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='레거시 값 → 새 코드 대응표';

-- TV 상영 상태 (MergedTVShow.tvStatus). DIS(TV발견)는 공개 상태가 아니라 수집 경로 표시라서 RELEASED로 둔다
INSERT INTO code_map (code_group, legacy_code, new_code, note) VALUES
 ('TV_STATUS', 'OA',  'RETURNING', '상영중'),
 ('TV_STATUS', 'CD',  'ENDED',     '상영완료'),
 ('TV_STATUS', 'UC',  'PLANNED',   '상영예정'),
 ('TV_STATUS', 'DIS', 'RELEASED',  'TV발견 (상태 정보 없음)');

-- 애니 방영 상태 (cartoon_video.status)
INSERT INTO code_map (code_group, legacy_code, new_code, note) VALUES
 ('CARTOON_STATUS', '예정', 'PLANNED',   NULL),
 ('CARTOON_STATUS', '진행', 'RETURNING', NULL),
 ('CARTOON_STATUS', '완료', 'ENDED',     NULL),
 ('CARTOON_STATUS', '중단', 'CANCELED',  NULL);

-- 한국어 나라 이름 → ISO 3166-1 alpha-2 (movies.movie_country)
INSERT INTO code_map (code_group, legacy_code, new_code) VALUES
 ('COUNTRY_KO', '한국', 'KR'), ('COUNTRY_KO', '대한민국', 'KR'), ('COUNTRY_KO', '미국', 'US'),
 ('COUNTRY_KO', '일본', 'JP'), ('COUNTRY_KO', '중국', 'CN'), ('COUNTRY_KO', '홍콩', 'HK'),
 ('COUNTRY_KO', '대만', 'TW'), ('COUNTRY_KO', '영국', 'GB'), ('COUNTRY_KO', '프랑스', 'FR'),
 ('COUNTRY_KO', '독일', 'DE'), ('COUNTRY_KO', '이탈리아', 'IT'), ('COUNTRY_KO', '스페인', 'ES'),
 ('COUNTRY_KO', '캐나다', 'CA'), ('COUNTRY_KO', '호주', 'AU'), ('COUNTRY_KO', '인도', 'IN'),
 ('COUNTRY_KO', '뉴질랜드', 'NZ'), ('COUNTRY_KO', '태국', 'TH');

-- 레거시 관람 등급 → 표준 등급 (KR 기준)
INSERT INTO code_map (code_group, legacy_code, new_code) VALUES
 ('AGE_RATING', '전체', 'ALL'), ('AGE_RATING', '12세', '12'), ('AGE_RATING', '15세', '15'),
 ('AGE_RATING', '청불', '19'), ('AGE_RATING', '19세', '19');

-- =====================================================================================
-- 2. 장르 기준 데이터 (content_genre)
-- 설명: TMDB 영화·TV 장르 ID를 내부 장르 코드 하나로 모은다
-- =====================================================================================
INSERT INTO content_genre (genre_code, name_ko, name_en, tmdb_movie_id, tmdb_tv_id) VALUES
 ('ACTION',      '액션',       'Action',          28,    10759),
 ('ADVENTURE',   '모험',       'Adventure',       12,    10759),
 ('ANIMATION',   '애니메이션', 'Animation',       16,    16),
 ('COMEDY',      '코미디',     'Comedy',          35,    35),
 ('CRIME',       '범죄',       'Crime',           80,    80),
 ('DOCUMENTARY', '다큐멘터리', 'Documentary',     99,    99),
 ('DRAMA',       '드라마',     'Drama',           18,    18),
 ('FAMILY',      '가족',       'Family',          10751, 10751),
 ('FANTASY',     '판타지',     'Fantasy',         14,    10765),
 ('HISTORY',     '역사',       'History',         36,    NULL),
 ('HORROR',      '공포',       'Horror',          27,    NULL),
 ('MUSIC',       '음악',       'Music',           10402, NULL),
 ('MYSTERY',     '미스터리',   'Mystery',         9648,  9648),
 ('ROMANCE',     '로맨스',     'Romance',         10749, NULL),
 ('SF',          'SF',         'Science Fiction', 878,   10765),
 ('THRILLER',    '스릴러',     'Thriller',        53,    NULL),
 ('WAR',         '전쟁',       'War',             10752, 10768),
 ('WESTERN',     '서부',       'Western',         37,    37),
 ('KIDS',        '키즈',       'Kids',            NULL,  10762),
 ('REALITY',     '리얼리티',   'Reality',         NULL,  10764);

-- 한국어 장르 이름 → 내부 장르 코드 (movies.movie_type, cartoon_video.genre)
INSERT INTO code_map (code_group, legacy_code, new_code)
SELECT 'GENRE_KO', g.name_ko, g.genre_code FROM content_genre g;
INSERT INTO code_map (code_group, legacy_code, new_code) VALUES
 ('GENRE_KO', '공상과학', 'SF'), ('GENRE_KO', '멜로', 'ROMANCE'), ('GENRE_KO', '애니', 'ANIMATION');

-- =====================================================================================
-- 3. 코드 변환 함수 (fn_map_code)
-- 설명: 대응이 없으면 NULL을 돌려준다. 앞뒤 공백은 무시한다
-- =====================================================================================
DELIMITER $$
CREATE FUNCTION fn_map_code(
    p_group VARCHAR(30),                                              -- 코드 그룹
    p_value VARCHAR(100)                                              -- 레거시 값
) RETURNS VARCHAR(30)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_code VARCHAR(30);
    SELECT new_code INTO v_code
      FROM code_map
     WHERE code_group = p_group
       AND legacy_code = TRIM(p_value)
     LIMIT 1;
    RETURN v_code;
END$$

-- =====================================================================================
-- 4. 날짜 변환 함수 (fn_try_date)
-- 설명: YYYY-MM-DD, YYYY.MM.DD, YYYYMMDD를 받아들이고 그 밖의 값·없는 날짜는 NULL
-- =====================================================================================
CREATE FUNCTION fn_try_date(
    p_value VARCHAR(30)                                               -- 레거시 날짜 문자열
) RETURNS DATE
DETERMINISTIC
NO SQL
BEGIN
    DECLARE v_text  VARCHAR(30) DEFAULT REPLACE(REPLACE(TRIM(p_value), '.', '-'), '/', '-');
    DECLARE v_year  INT;
    DECLARE v_month INT;
    DECLARE v_day   INT;
    DECLARE v_first DATE;
    IF v_text IS NULL OR v_text = '' THEN
        RETURN NULL;
    END IF;
    IF v_text REGEXP '^[0-9]{8}$' THEN
        SET v_text = CONCAT(SUBSTR(v_text, 1, 4), '-', SUBSTR(v_text, 5, 2), '-', SUBSTR(v_text, 7, 2));
    END IF;
    IF v_text NOT REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN
        RETURN NULL;
    END IF;
    -- 2020-13-45처럼 형식만 맞는 값을 걸러낸다. STR_TO_DATE는 엄격 모드에서 NULL 대신 오류를 내므로 쓰지 않는다
    SET v_year = CAST(SUBSTR(v_text, 1, 4) AS UNSIGNED);
    SET v_month = CAST(SUBSTR(v_text, 6, 2) AS UNSIGNED);
    SET v_day = CAST(SUBSTR(v_text, 9, 2) AS UNSIGNED);
    IF v_year < 1800 OR v_month NOT BETWEEN 1 AND 12 THEN
        RETURN NULL;
    END IF;
    SET v_first = MAKEDATE(v_year, 1) + INTERVAL (v_month - 1) MONTH;
    IF v_day NOT BETWEEN 1 AND DAY(LAST_DAY(v_first)) THEN
        RETURN NULL;
    END IF;
    RETURN v_first + INTERVAL (v_day - 1) DAY;
END$$

-- =====================================================================================
-- 5. 상영 시간 변환 함수 (fn_try_runtime)
-- 설명: '108분', '108', '1시간 48분'을 분으로 바꾼다. 해석할 수 없으면 NULL
-- =====================================================================================
CREATE FUNCTION fn_try_runtime(
    p_value VARCHAR(30)                                               -- 레거시 상영 시간 문자열
) RETURNS SMALLINT
DETERMINISTIC
NO SQL
BEGIN
    DECLARE v_text VARCHAR(30) DEFAULT REPLACE(TRIM(p_value), ' ', '');
    IF v_text REGEXP '^[0-9]{1,3}(분)?$' THEN
        RETURN CAST(REPLACE(v_text, '분', '') AS UNSIGNED);
    END IF;
    IF v_text REGEXP '^[0-9]{1,2}시간([0-9]{1,2}분)?$' THEN
        RETURN CAST(SUBSTRING_INDEX(v_text, '시간', 1) AS UNSIGNED) * 60
             + COALESCE(CAST(NULLIF(REPLACE(SUBSTRING_INDEX(v_text, '시간', -1), '분', ''), '') AS UNSIGNED), 0);
    END IF;
    RETURN NULL;
END$$
DELIMITER ;
