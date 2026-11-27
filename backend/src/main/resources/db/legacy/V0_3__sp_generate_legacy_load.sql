-- =====================================================================================
-- Flyway 마이그레이션: 부하 실험용 구시스템 데이터 생성 프로시저
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V0.3
-- 작성일: 2026-11-22
-- 설명: 백필 처리량을 재려고 구시스템 행을 대량으로 만든다. 실제 구시스템 DB에는 넣지 않는다 (db/legacy 위치)
-- =====================================================================================

DELIMITER $$

-- =====================================================================================
-- 1. 구시스템 대량 데이터 생성 (sp_generate_legacy_load)
-- 설명: 영화 p_movies건, TV p_series건을 만든다. 배우·장르·국가 문자열과 TMDB ID 형식을 실제 데이터처럼 섞는다
-- =====================================================================================
CREATE PROCEDURE sp_generate_legacy_load(
    IN p_movies INT,                                                  -- 만들 영화 수
    IN p_series INT                                                   -- 만들 TV 시리즈 수
)
BEGIN
    SET SESSION cte_max_recursion_depth = GREATEST(1000, p_movies, p_series);

    INSERT INTO movies (movie_title, movie_content, movie_type, movie_open_date, movie_rating, movie_runtime,
                        movie_actor, movie_country, movie_director, movie_age)
    WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < p_movies)
    SELECT CONCAT('부하 영화 ', i),
           CONCAT('부하 실험용 줄거리 ', i),
           ELT(1 + i % 5, '드라마', '액션, 스릴러', '코미디, 가족', 'SF, 모험', '로맨스, 드라마'),
           DATE_FORMAT(DATE '2000-01-01' + INTERVAL (i % 9000) DAY, '%Y-%m-%d'),
           ROUND(1 + (i % 90) / 10, 1),
           CONCAT(80 + i % 90, '분'),
           CONCAT('배우', i % 3000, ', 배우', (i + 7) % 3000, ', 배우', (i + 13) % 3000),
           ELT(1 + i % 6, '한국', '미국', '일본', '영국', '프랑스', '대한민국'),
           CONCAT('감독', i % 800),
           ELT(1 + i % 4, '전체', '12세', '15세', '청불')
      FROM n;

    INSERT INTO MergedTVShow (tvNumber, tvName, overview, first_air_date, popularity, vote_average, vote_count,
                              genreIds, originCountry, originalLanguage, originalName, tvStatus)
    WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < p_series)
    SELECT CAST(9000000 + i AS CHAR),
           CONCAT('부하 시리즈 ', i),
           CONCAT('부하 실험용 개요 ', i),
           DATE_FORMAT(DATE '2005-01-01' + INTERVAL (i % 7000) DAY, '%Y-%m-%d'),
           i % 500, ROUND(1 + (i % 90) / 10, 1), i % 10000,
           ELT(1 + i % 3, '18', '10759,18', '10765,9648'),
           ELT(1 + i % 3, 'KR', 'US', 'JP'),
           ELT(1 + i % 3, 'ko', 'en', 'ja'),
           CONCAT('Load Series ', i),
           ELT(1 + i % 3, 'OA', 'CD', 'UC')
      FROM n;
END$$

DELIMITER ;
