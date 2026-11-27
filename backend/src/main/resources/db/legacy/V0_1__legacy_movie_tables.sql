-- =====================================================================================
-- Flyway 마이그레이션: 구시스템(레거시) 테이블
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V0.1
-- 작성일: 2026-11-22
-- 설명: 2022년 영화 메타 서비스(movieMbti)의 테이블을 MyBatis 매퍼·DTO 컬럼으로 복원한 구시스템이다.
--       날짜·상영시간이 문자열이고, 배우·감독·국가가 한 칸에 쉼표로 들어 있고, 외부 표준 ID가 거의 없는 구조를 그대로 재현한다.
--       실제 구시스템 DB에 붙일 때는 db/legacy 위치를 빼고(구시스템 테이블은 이미 있다) V1부터 적용한다
-- =====================================================================================


-- 영화 (KOBIS 박스오피스·관리자 등록)
CREATE TABLE IF NOT EXISTS movies (
    movie_seq       INT AUTO_INCREMENT PRIMARY KEY,
    movie_title     VARCHAR(200),
    movie_content   TEXT,
    movie_img       VARCHAR(300),
    movie_type      VARCHAR(100),
    movie_open_date VARCHAR(20),
    movie_rating    DECIMAL(4,1),
    movie_runtime   VARCHAR(20),
    movie_actor     VARCHAR(1000),
    movie_country   VARCHAR(100),
    admin_id        VARCHAR(50),
    movie_director  VARCHAR(200),
    movie_cnt       INT DEFAULT 0,
    movie_age       VARCHAR(20)
) DEFAULT CHARSET=utf8mb4;

-- TV 시리즈 (TMDB 응답을 그대로 저장. tvNumber가 TMDB TV ID)
CREATE TABLE IF NOT EXISTS MergedTVShow (
    mgSeq            INT AUTO_INCREMENT PRIMARY KEY,
    tvNumber         VARCHAR(20),
    tvName           VARCHAR(200),
    overview         TEXT,
    first_air_date   VARCHAR(20),
    popularity       DECIMAL(10,3),
    vote_average     DECIMAL(4,1),
    vote_count       INT,
    poster_path      VARCHAR(300),
    genreIds         VARCHAR(100),
    originCountry    VARCHAR(50),
    originalLanguage VARCHAR(10),
    originalName     VARCHAR(200),
    tvStatus         VARCHAR(10)
) DEFAULT CHARSET=utf8mb4;

-- 애니메이션 (화수 범위가 문자열)
CREATE TABLE IF NOT EXISTS cartoon_video (
    ctSeq             INT AUTO_INCREMENT PRIMARY KEY,
    cartoonTitle      VARCHAR(200),
    englishTitle      VARCHAR(200),
    episode_start     VARCHAR(10),
    episode_end       VARCHAR(10),
    status            VARCHAR(10),
    airing_date       VARCHAR(20),
    category1         VARCHAR(100),
    productionCompany VARCHAR(200),
    genre             VARCHAR(100),
    synopsis          TEXT,
    posterPath        VARCHAR(300),
    voteAverage       VARCHAR(10)
) DEFAULT CHARSET=utf8mb4;
