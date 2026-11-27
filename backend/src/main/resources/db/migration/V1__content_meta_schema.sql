-- =====================================================================================
-- Flyway 마이그레이션: 콘텐츠 메타 핵심 스키마
-- 프로젝트: Content Meta Migration (content-meta-migration)
-- 마이그레이션 버전: V1
-- 작성일: 2026-11-22
-- 설명: 작품 → 시즌 → 에피소드, 인물·출연, 언어별 현지화, 지역별 공개 정보, 장르,
--       외부 표준 식별자(TMDB·IMDB·KOBIS) 매핑 테이블을 만든다.
--       레거시 테이블(movies, MergedTVShow, cartoon_video)은 건드리지 않고 옆에 새로 만든다 (확장 단계)
-- =====================================================================================

-- =====================================================================================
-- 1. 작품 (content_title)
-- 설명: 영화·시리즈의 대표 단위. 원본 시스템 + 원본 키 유니크 제약으로 같은 레거시 행이 두 번 들어오지 않는다
-- =====================================================================================
CREATE TABLE content_title (
    title_id          BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '작품 ID (자동증가)',
    title_type        VARCHAR(10)  NOT NULL                               COMMENT '작품 유형 (MOVIE:영화, SERIES:시리즈)',
    original_title    VARCHAR(300) NOT NULL                               COMMENT '원제',
    original_language VARCHAR(10)                                         COMMENT '원어 (ISO 639-1, 예: ko, en, ja)',
    release_date      DATE                                                COMMENT '최초 공개일 (영화 개봉일, 시리즈 첫 방영일)',
    runtime_min       SMALLINT                                            COMMENT '상영 시간(분). 시리즈는 비움',
    status            VARCHAR(10)  NOT NULL DEFAULT 'RELEASED'            COMMENT '상태 (PLANNED:공개 예정, RETURNING:방영 중, ENDED:완결, CANCELED:중단, RELEASED:공개됨, DELETED:원본 삭제)',
    is_animation      TINYINT(1)   NOT NULL DEFAULT 0                     COMMENT '애니메이션 여부 (1:예, 0:아니오)',
    popularity        DECIMAL(10,3)                                       COMMENT '인기도 (원천 값 그대로)',
    vote_average      DECIMAL(3,1)                                        COMMENT '평점 (10점 만점)',
    vote_count        INT                                                 COMMENT '평가 수',
    poster_path       VARCHAR(300)                                        COMMENT '포스터 이미지 경로',
    origin_system     VARCHAR(30)  NOT NULL                               COMMENT '원본 시스템 (LEGACY_MOVIES, LEGACY_TV, LEGACY_CARTOON, MANUAL)',
    origin_key        VARCHAR(100) NOT NULL                               COMMENT '원본 키 (레거시 순번 등)',
    meta_version      INT          NOT NULL DEFAULT 1                     COMMENT '메타 버전 (내용이 바뀔 때마다 1 증가)',
    migration_run_id  BIGINT                                              COMMENT '마지막으로 반영한 이관 작업 ID',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)  COMMENT '생성일시',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '수정일시',
    PRIMARY KEY (title_id),
    CONSTRAINT uq_content_title_origin UNIQUE (origin_system, origin_key),
    CONSTRAINT ck_content_title_type CHECK (title_type IN ('MOVIE', 'SERIES')),
    CONSTRAINT ck_content_title_status CHECK (status IN ('PLANNED', 'RETURNING', 'ENDED', 'CANCELED', 'RELEASED', 'DELETED')),
    KEY ix_content_title_release (title_type, release_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='작품 (영화·시리즈)';

-- =====================================================================================
-- 2. 시즌 (content_season)
-- 설명: 시리즈의 시즌. 영화는 시즌이 없다
-- =====================================================================================
CREATE TABLE content_season (
    season_id      BIGINT       NOT NULL AUTO_INCREMENT                   COMMENT '시즌 ID (자동증가)',
    title_id       BIGINT       NOT NULL                                  COMMENT '작품 ID',
    season_no      SMALLINT     NOT NULL                                  COMMENT '시즌 번호 (0:스페셜)',
    season_name    VARCHAR(300)                                           COMMENT '시즌 이름',
    air_date       DATE                                                   COMMENT '시즌 첫 방영일',
    episode_count  SMALLINT                                               COMMENT '에피소드 수',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)     COMMENT '생성일시',
    PRIMARY KEY (season_id),
    CONSTRAINT uq_content_season UNIQUE (title_id, season_no),
    CONSTRAINT fk_content_season_title FOREIGN KEY (title_id) REFERENCES content_title (title_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='시즌';

-- =====================================================================================
-- 3. 에피소드 (content_episode)
-- 설명: 시즌의 회차. 시즌 안에서 회차 번호가 겹치지 않는다
-- =====================================================================================
CREATE TABLE content_episode (
    episode_id     BIGINT       NOT NULL AUTO_INCREMENT                   COMMENT '에피소드 ID (자동증가)',
    season_id      BIGINT       NOT NULL                                  COMMENT '시즌 ID',
    episode_no     SMALLINT     NOT NULL                                  COMMENT '회차 번호',
    episode_name   VARCHAR(300)                                           COMMENT '회차 제목',
    air_date       DATE                                                   COMMENT '방영일',
    runtime_min    SMALLINT                                               COMMENT '회차 길이(분)',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)     COMMENT '생성일시',
    PRIMARY KEY (episode_id),
    CONSTRAINT uq_content_episode UNIQUE (season_id, episode_no),
    CONSTRAINT fk_content_episode_season FOREIGN KEY (season_id) REFERENCES content_season (season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='에피소드';

-- =====================================================================================
-- 4. 인물 (content_person)
-- 설명: 배우·감독 등. 레거시는 이름 문자열만 있어 동명이인을 가를 수 없으므로 name_only=1로 표시하고
--       외부 인물 ID(TMDB·IMDB)가 붙으면 0으로 바꾼다
-- =====================================================================================
CREATE TABLE content_person (
    person_id      BIGINT       NOT NULL AUTO_INCREMENT                   COMMENT '인물 ID (자동증가)',
    person_name    VARCHAR(200) NOT NULL                                  COMMENT '표시 이름',
    original_name  VARCHAR(200)                                           COMMENT '원어 이름',
    name_only      TINYINT(1)   NOT NULL DEFAULT 1                        COMMENT '이름만으로 만든 인물 여부 (1:동명이인 확인 필요, 0:외부 ID로 확인됨)',
    origin_system  VARCHAR(30)  NOT NULL                                  COMMENT '원본 시스템 (LEGACY_NAME, TMDB, MANUAL)',
    origin_key     VARCHAR(200) NOT NULL                                  COMMENT '원본 키 (레거시는 이름, TMDB는 인물 ID)',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)     COMMENT '생성일시',
    PRIMARY KEY (person_id),
    CONSTRAINT uq_content_person_origin UNIQUE (origin_system, origin_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='인물';

-- =====================================================================================
-- 5. 출연·제작진 (content_credit)
-- 설명: 작품(또는 에피소드)과 인물의 연결. 역할과 배역 이름을 남긴다
-- =====================================================================================
CREATE TABLE content_credit (
    credit_id      BIGINT       NOT NULL AUTO_INCREMENT                   COMMENT '출연 ID (자동증가)',
    title_id       BIGINT       NOT NULL                                  COMMENT '작품 ID',
    person_id      BIGINT       NOT NULL                                  COMMENT '인물 ID',
    role_type      VARCHAR(10)  NOT NULL                                  COMMENT '역할 (CAST:출연, DIRECTOR:감독, WRITER:각본, CREATOR:원작·기획)',
    character_name VARCHAR(200) NOT NULL DEFAULT ''                       COMMENT '배역 이름 (없으면 빈 문자열)',
    credit_order   SMALLINT     NOT NULL DEFAULT 0                        COMMENT '표시 순서',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)     COMMENT '생성일시',
    PRIMARY KEY (credit_id),
    CONSTRAINT uq_content_credit UNIQUE (title_id, person_id, role_type, character_name),
    CONSTRAINT ck_content_credit_role CHECK (role_type IN ('CAST', 'DIRECTOR', 'WRITER', 'CREATOR')),
    CONSTRAINT fk_content_credit_title FOREIGN KEY (title_id) REFERENCES content_title (title_id),
    CONSTRAINT fk_content_credit_person FOREIGN KEY (person_id) REFERENCES content_person (person_id),
    KEY ix_content_credit_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='출연·제작진';

-- =====================================================================================
-- 6. 언어별 현지화 (content_localization)
-- 설명: 작품의 언어별 제목·줄거리. 언어 코드는 BCP 47 (ko, en, ja, zh-TW)
-- =====================================================================================
CREATE TABLE content_localization (
    title_id       BIGINT       NOT NULL                                  COMMENT '작품 ID',
    lang_code      VARCHAR(10)  NOT NULL                                  COMMENT '언어 코드 (BCP 47)',
    local_title    VARCHAR(300) NOT NULL                                  COMMENT '현지화 제목',
    overview       TEXT                                                   COMMENT '현지화 줄거리',
    source         VARCHAR(20)  NOT NULL                                  COMMENT '출처 (LEGACY, TMDB, MANUAL, MT:기계번역)',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '수정일시',
    PRIMARY KEY (title_id, lang_code),
    CONSTRAINT fk_content_localization_title FOREIGN KEY (title_id) REFERENCES content_title (title_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='언어별 현지화';

-- =====================================================================================
-- 7. 지역별 공개 정보 (content_title_region)
-- 설명: 나라마다 다른 공개일·관람 등급. 지역 코드는 ISO 3166-1 alpha-2
-- =====================================================================================
CREATE TABLE content_title_region (
    title_id       BIGINT       NOT NULL                                  COMMENT '작품 ID',
    region_code    CHAR(2)      NOT NULL                                  COMMENT '지역 코드 (ISO 3166-1 alpha-2)',
    release_date   DATE                                                   COMMENT '그 지역 공개일',
    age_rating     VARCHAR(20)                                            COMMENT '그 지역 관람 등급 (예: 12, 15, 19, ALL)',
    source         VARCHAR(20)  NOT NULL                                  COMMENT '출처 (LEGACY, TMDB, KOBIS, MANUAL)',
    PRIMARY KEY (title_id, region_code),
    CONSTRAINT fk_content_title_region_title FOREIGN KEY (title_id) REFERENCES content_title (title_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='지역별 공개 정보';

-- =====================================================================================
-- 8. 제작 국가 (content_title_country)
-- 설명: 작품을 만든 나라. 공동 제작이면 여러 행
-- =====================================================================================
CREATE TABLE content_title_country (
    title_id       BIGINT       NOT NULL                                  COMMENT '작품 ID',
    country_code   CHAR(2)      NOT NULL                                  COMMENT '제작 국가 (ISO 3166-1 alpha-2)',
    PRIMARY KEY (title_id, country_code),
    CONSTRAINT fk_content_title_country_title FOREIGN KEY (title_id) REFERENCES content_title (title_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='제작 국가';

-- =====================================================================================
-- 9. 장르 (content_genre, content_title_genre)
-- 설명: 내부 장르 코드 하나에 TMDB 영화·TV 장르 ID를 대응시킨다 (TMDB는 영화와 TV의 장르 ID 체계가 다르다)
-- =====================================================================================
CREATE TABLE content_genre (
    genre_code     VARCHAR(20)  NOT NULL                                  COMMENT '내부 장르 코드',
    name_ko        VARCHAR(50)  NOT NULL                                  COMMENT '한국어 이름',
    name_en        VARCHAR(50)  NOT NULL                                  COMMENT '영어 이름',
    tmdb_movie_id  INT                                                    COMMENT 'TMDB 영화 장르 ID',
    tmdb_tv_id     INT                                                    COMMENT 'TMDB TV 장르 ID',
    PRIMARY KEY (genre_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='장르';

CREATE TABLE content_title_genre (
    title_id       BIGINT       NOT NULL                                  COMMENT '작품 ID',
    genre_code     VARCHAR(20)  NOT NULL                                  COMMENT '내부 장르 코드',
    PRIMARY KEY (title_id, genre_code),
    CONSTRAINT fk_content_title_genre_title FOREIGN KEY (title_id) REFERENCES content_title (title_id),
    CONSTRAINT fk_content_title_genre_genre FOREIGN KEY (genre_code) REFERENCES content_genre (genre_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='작품별 장르';

-- =====================================================================================
-- 10. 외부 표준 식별자 (content_external_id)
-- 설명: 작품·시즌·에피소드·인물을 TMDB·IMDB·KOBIS ID에 연결한다.
--       TMDB는 영화와 TV의 ID 공간이 따로라서 제공처를 TMDB_MOVIE, TMDB_TV, TMDB_PERSON으로 나눈다.
--       같은 외부 ID가 두 내부 대상에 붙지 않고(uq_external_id_value), 한 대상에 같은 제공처 ID가 둘 붙지 않는다(uq_external_id_owner)
-- =====================================================================================
CREATE TABLE content_external_id (
    external_id_seq BIGINT      NOT NULL AUTO_INCREMENT                   COMMENT '외부 식별자 순번 (자동증가)',
    entity_type    VARCHAR(10)  NOT NULL                                  COMMENT '대상 유형 (TITLE, SEASON, EPISODE, PERSON)',
    entity_id      BIGINT       NOT NULL                                  COMMENT '대상 ID (title_id, season_id, episode_id, person_id)',
    provider       VARCHAR(20)  NOT NULL                                  COMMENT '제공처 (TMDB_MOVIE, TMDB_TV, TMDB_PERSON, IMDB, KOBIS)',
    external_id    VARCHAR(50)  NOT NULL                                  COMMENT '제공처의 ID (IMDB tt0000000·nm0000000, KOBIS 영화코드 8자리)',
    match_method   VARCHAR(20)  NOT NULL                                  COMMENT '연결 방법 (LEGACY:레거시 값, API:외부 API 응답, MANUAL:운영자 확인)',
    verified       TINYINT(1)   NOT NULL DEFAULT 0                        COMMENT '운영자 확인 여부 (1:확인, 0:미확인)',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)     COMMENT '생성일시',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '수정일시',
    PRIMARY KEY (external_id_seq),
    CONSTRAINT uq_external_id_value UNIQUE (provider, external_id),
    CONSTRAINT uq_external_id_owner UNIQUE (entity_type, entity_id, provider),
    CONSTRAINT ck_external_id_entity CHECK (entity_type IN ('TITLE', 'SEASON', 'EPISODE', 'PERSON')),
    CONSTRAINT ck_external_id_provider CHECK (provider IN ('TMDB_MOVIE', 'TMDB_TV', 'TMDB_PERSON', 'IMDB', 'KOBIS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='외부 표준 식별자 매핑';
