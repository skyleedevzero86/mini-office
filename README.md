# Content Meta Migration

> 2022년에 만든 영화 메타 서비스(movieMbti)의 레거시 테이블을 **TMDB·IMDB·KOBIS 외부 표준 ID와 작품·시즌·에피소드·인물·언어·지역 메타 스키마**로,
> **서비스를 멈추지 않고** 옮기는 프로젝트입니다. 검증·적재·대조는 MySQL 프로시저로, 운영(이관 단계·배치·품질)은 Spring Boot(Java 21)로,
> 목록·상세·통계·모니터링 화면은 React + TypeScript(pnpm)로 다룹니다. TMDB 키를 넣으면 포스터·줄거리·출연진을 TMDB에서 가져옵니다.

> **서버 없이 먼저 보기**
> - [`dummy/index.html`](dummy/index.html): 운영 화면(대시보드·작품 목록·상세·통계·모니터링·이관)을 더미 데이터로 띄운 정적 데모. 순수 HTML·CSS·JavaScript(`dummy/demo/`)라 더블클릭으로 열린다
> - [`dummy/guide/flow.html`](dummy/guide/flow.html): 백엔드가 하는 일(사전 검증 → 백필 → 동기화 → 대조 → 읽기 전환)을 단계별로 눌러 보는 시뮬레이터

> **밝혀 두는 것**: 실제 운영 서비스가 아닙니다. 구시스템 테이블은 movieMbti의 MyBatis 매퍼·DTO로 복원했고, 데이터는 공개 작품 정보로 만든 샘플과 부하 실험용 합성 데이터입니다.

<br/>

## 이 프로젝트가 증명하는 것

| # | 주제 | 한 줄 요약 |
|---|---|---|
| 1 | [외부 표준 ID 매핑과 콘텐츠 메타 스키마](#1-외부-표준-id-매핑과-콘텐츠-메타-스키마) | 문자열 한 칸에 몰린 레거시를 작품·시즌·에피소드·인물·언어·지역으로 나누고, 자체 ID를 TMDB·IMDB·KOBIS에 잇는다 |
| 2 | [레거시 → 신규 무중단 마이그레이션](#2-레거시--신규-무중단-마이그레이션) | 확장 → 변경 기록 → 묶음 백필 → 변경분 동기화 → 대조 → 읽기 전환을 끝까지 돌리고 테스트로 재현한다 |
| 3 | [배치·SLA·장애 대응·데이터 품질](#3-배치sla장애-대응데이터-품질) | 배치마다 SLA를 두고, 실패는 묶음 단위로 되돌린 뒤 이어 가며, 품질 규칙 9개로 메타 오염을 잡는다 |
| 4 | [TMDB 보강과 운영 화면](#4-tmdb-보강과-운영-화면) | 후보 검색 → 운영자 연결 → 상세 조회 → 반영. 반영해도 대조가 깨지지 않게 지문 컬럼은 건드리지 않는다 |

설계 판단의 근거와 공연 도메인(IdolGlow)과의 차이는 [docs/design.md](docs/design.md)에 있습니다.

---

### 1. 외부 표준 ID 매핑과 콘텐츠 메타 스키마

**문제**<br/>
레거시는 배우·감독·국가가 한 칸에 쉼표로 들어 있고(`'윤계상, 박용우, 임지연'`), 영화에는 외부 표준 ID 칸이 없으며, TV만 `tvNumber`에 TMDB ID를 들고 있었다. 시즌·에피소드도 없어서 애니의 화수는 `'1'`~`'26'` 문자열이었다.

**판단**

- 작품(`content_title`) → 시즌 → 에피소드를 계층으로 두고, 인물·출연, 언어별 현지화(BCP 47), 지역별 공개일·등급(ISO 3166), 제작 국가, 장르를 따로 뺐다
- **외부 표준 ID ↔ 자체 ID 매핑 테이블**(`content_external_id`): 작품·시즌·에피소드·인물 하나에 제공처별 ID가 붙는다
  - TMDB는 영화와 TV의 ID 공간이 따로라서 제공처를 `TMDB_MOVIE`, `TMDB_TV`, `TMDB_PERSON`으로 나눴다
  - 같은 외부 ID가 두 대상에 붙지 않고(`uq_external_id_value`), 한 대상에 같은 제공처 ID가 둘 붙지 않는다(`uq_external_id_owner`)
  - **자동 매칭은 이미 붙은 값을 덮지 않는다.** 바꾸는 건 운영자가 `sp_external_id_cud`로 하고, `work_log`에 누가 언제 바꿨는지 남는다
- 레거시는 이름만 있어서 동명이인을 가를 수 없다. 이름으로 만든 인물은 `name_only=1`로 두고, 외부 인물 ID가 붙으면 0으로 바뀐다
- 언어별 제목은 레거시 동기화가 덮어쓰지만, 운영자가 고친 제목(`source=MANUAL`)은 덮지 않는다
- TMDB 영화·TV 장르 ID를 내부 장르 코드 하나에 모은다 (`content_genre`)

**결과**

- 샘플 원본 16행 → ERROR 5행을 빼고 작품 11, 출연 17, 에피소드 38(26화 + 12화)
- `GET /api/catalog/by-external/TMDB_TV/1399` → "Game of Thrones", 언어별 제목 `en`·`ko`

**코드·테스트**: `V1__content_meta_schema.sql`, `V5__sp_apply_legacy_migration.sql`, `V9__sp_cud_procedures.sql`, `catalog/`, `CatalogEditIntegrationTest`

---

### 2. 레거시 → 신규 무중단 마이그레이션

**문제**<br/>
레거시 화면은 이관하는 동안에도 계속 쓰인다. 한 번에 옮기면 그 사이 바뀐 데이터를 잃고, 큰 트랜잭션 하나로 옮기면 레거시 테이블을 오래 잠근다.

**판단**

| 단계 | 하는 일 | 되돌리기 |
|---|---|---|
| 1. 확장 | 새 테이블·프로시저를 레거시 옆에 만든다. 레거시는 건드리지 않는다 | 새 테이블만 지운다 |
| 2. 변경 기록 | 레거시 INSERT·UPDATE·DELETE를 트리거가 **키만** `legacy_change_log`에 남긴다 | 트리거 삭제 |
| 3. 백필 | 체크포인트 다음 원본을 묶음으로 검증 → 반영하고 **묶음마다 커밋**한다 | 체크포인트에서 이어 간다 |
| 4. 동기화 | 쌓인 변경 키를 `FOR UPDATE SKIP LOCKED`로 묶음씩 꺼내 반영한다 | 다시 돌리면 된다 |
| 5. 대조 | 건수와 내용 지문(CRC32)으로 누락·불일치·고아를 센다 | 읽기만 한다 |
| 6. 읽기 전환 | 최근 2시간 안의 대조가 0/0/0일 때만 `NEW`로 바꾼다 | 언제든 `LEGACY`로 되돌린다 |
| 7. 축소 | 화면을 새 API로 옮긴 뒤 트리거·레거시 테이블을 지운다 | (이 프로젝트 범위 밖) |

- 백필과 동기화가 **같은 적재 프로시저**(`sp_upsert_legacy_keys`)를 쓴다. 원본 키 유니크 + 업서트라 순서가 엇갈려도, 몇 번 다시 돌려도 결과가 같다
- 동기화는 "지금 원본 상태"로 반영한다. 같은 키가 세 번 바뀌어도 한 번 반영하고, 원본이 없어졌으면 작품을 `DELETED`로 바꾼다
- 조회수만 바뀐 UPDATE(`movie_cnt`, 상세 조회 때마다 오르는 `vote_count`)는 기록하지 않는다
- 검증 결과는 ERROR(적재 제외)와 INFO(적재하고 안내)로 나눠 `migration_issue`에 사유와 함께 남긴다

**일부러 깨 보고 확인한 것** (`LegacyMigrationScenarioTest`)

| 실험 | 결과 |
|---|---|
| 사전 검증 | 제목 없음·날짜 2020/13/45·제목+개봉일 중복·TMDB ID 중복·상태 코드 XX를 ERROR 5건으로, 대응 없는 나라·상영시간·화수 범위를 INFO로 잡음 |
| 백필 (묶음 3) | 3묶음, 체크포인트 정확, 재실행해도 작품·출연·에피소드·인물 수와 meta_version 합이 그대로 |
| 레거시에서 수정·등록·삭제 | 동기화 전 대조 누락 1 / 불일치 1 / 고아 1 → 읽기 전환 거절 → 동기화 → 0/0/0 → 전환 |
| 묶음 중간 실패 (장르 테이블을 치움) | 그 묶음만 롤백(일부 적재 0), `FAILED`와 사유, 실행 횟수 1 → 고친 뒤 체크포인트에서 이어서 완료, 실행 횟수 2 |

**코드·테스트**: `V3`~`V7`, `migration/application/MigrationService.java`, `ReadSourceSwitch.java`, `LegacyMigrationScenarioTest`

---

### 3. 배치·SLA·장애 대응·데이터 품질

**문제**<br/>
동기화가 조용히 멈추거나, 잘못된 외부 ID가 들어가거나, 회차가 빠져도 화면은 그대로 떠서 아무도 모른다.

**판단**

- 배치(`LEGACY_SYNC` 1분, `LEGACY_VERIFY` 매시간, `QUALITY_CHECK` 매일)마다 실행 SLA(허용 시간)와 신선도 SLA(마지막 성공 후 허용 간격)를 둔다
- `sp_batch_run_start`가 같은 배치의 겹침을 막는다 (서버가 여러 대여도 한 번만). 허용 시간의 3배를 넘긴 `RUNNING`은 멈춘 실행으로 정리한다
- 실패는 사유와 함께 기록하고 다음 주기에 다시 돈다. 묶음 단위 롤백·재개는 프로시저가 맡는다
- 품질 규칙은 테이블(`dq_rule`)로 관리하고, 임계값을 넘으면 FAIL로 판정해 예시 키 10개와 남긴다

| 코드 | 규칙 | 심각도 | 허용 |
|---|---|---|---|
| DQ001 | IMDB ID 형식 (`tt`·`nm` + 숫자 7자리 이상) | HIGH | 0 |
| DQ002 | TMDB(숫자)·KOBIS(숫자 8자리) 형식 | HIGH | 0 |
| DQ003 | 외부 ID가 하나도 없는 작품 | LOW | 100 |
| DQ004 | 한국어 현지화 누락 | HIGH | 0 |
| DQ005 | 시즌 없는 시리즈 | LOW | 50 |
| DQ006 | 에피소드 번호 빠짐 | HIGH | 0 |
| DQ007 | 이름만 있는 인물 | LOW | 1000 |
| DQ008 | 출연진 없는 영화 | LOW | 20 |
| DQ009 | 10분 넘게 반영되지 않은 레거시 변경 (동기화 멈춤) | HIGH | 0 |

**결과** (`OperationIntegrationTest`): 깨진 IMDB ID, 회차 하나 삭제, 30분 전 미반영 변경을 넣으면 DQ001·DQ006·DQ009가 FAIL. 허용 시간을 넘긴 실행은 SLA 위반, 예외는 `FAILED`와 사유로 남고, 겹친 실행은 시작하지 않는다.

**코드·테스트**: `V8__batch_sla_quality.sql`, `operation/`, `OperationIntegrationTest`

---

### 4. TMDB 보강과 운영 화면

**문제**<br/>
레거시 영화에는 외부 ID 칸이 없고 줄거리·포스터가 부실하다. 외부 데이터를 자동으로 덮어쓰면 틀린 매칭이 조용히 퍼지고, 반영한 값이 다음 레거시 동기화에 지워지거나 대조를 깨뜨릴 수 있다.

**판단**

- **자동으로 붙이지 않는다.** 화면에서 원제·연도로 TMDB 후보를 찾고(이미 다른 작품에 연결된 후보는 표시만), 운영자가 고른 것만 `sp_external_id_cud`로 연결한다
- 상세 화면은 연결된 `TMDB_MOVIE`·`TMDB_TV` ID로 포스터·줄거리·출연진·평점을 실시간으로 보여 준다 (메모리 캐시 30분, 키가 없으면 `configured=false`로 안내만)
- "TMDB 정보 반영"은 `sp_title_tmdb_apply`(V10)로 **포스터·IMDB ID·영어 제목/줄거리만** 저장한다
  - 대조 지문 컬럼(원제·공개일·상영시간·상태)은 레거시가 원천이라 바꾸지 않는다 → 반영 뒤에도 대조 0/0/0
  - 적재 프로시저는 레거시 포스터가 비어 있으면 기존 포스터를 지우지 않는다 (`COALESCE`)
  - 다른 작품이 쓰는 IMDB ID, 운영자 값(`MANUAL`)은 덮지 않고 무엇을 건너뛰었는지 `out_note`·`work_log`에 남긴다
  - TMDB 호출은 트랜잭션 밖에서 하고 프로시저 호출만 트랜잭션으로 묶는다 (외부 응답을 기다리며 잠금을 잡지 않게)
- 운영 화면 (`frontend/`, React 19 + TypeScript + Vite + TanStack Query + Recharts, pnpm)

| 화면 | 내용 |
|---|---|
| 대시보드 | 이관 단계(5단계 진행), 작품·에피소드·외부 ID 연결률·한국어 현지화율·동기화 대기·품질, SLA 신선도, 배치 추이 |
| 작품 목록 | 검색·유형·애니·장르 필터, 정렬 5종, 서버 페이징(전체 건수·페이지 수), 표/포스터 보기 |
| 작품 상세 | TMDB 포스터·배경·줄거리·출연진, 후보 검색·연결, 반영, 외부 ID C/U/D, 언어별 현지화 편집, 시즌·출연 |
| 통계 | 유형·상태·공개 연도·장르·제공처·언어별 분포, 최근 이관의 검증 문제 (차트마다 표 보기) |
| 모니터링 | SLA, 배치 실행 기록(필터·페이징), 배치 지금 실행, 최근 품질 점검·대조 결과 |
| 이관 | 읽기 원천 전환(조건 안내), 작업 만들기, 작업 기록 → 작업 상세(사전 검증·백필·동기화·대조 실행, 문제 목록 페이징) |

**코드·테스트**: `V10__sp_title_tmdb_apply.sql`, `tmdb/`, `dashboard/`, `catalog/`(페이지 조회), `TmdbIntegrationTest`(Mockito로 TMDB 응답을 바꿔 반영 → 레거시 동기화 → 대조 0/0/0), `CatalogPageIntegrationTest`, `DashboardIntegrationTest`

---

## 규모

**측정** (개발 환경 MySQL 8.0.46 단일 인스턴스, 구시스템 합성 데이터 `sp_generate_legacy_load`)

| 원본 | 묶음 크기 | 백필 | 결과 | 대조 |
|---|---|---|---|---|
| 영화 2만 + TV 5천 | 1,000 | 15.4초 (약 1,600건/초) | 작품 25,000, 출연 80,000 | 3.7초, 0/0/0 |

같은 실험이 `BackfillLoadTest`에 있다 (처리량은 로그로 남기고, 대조 0/0/0과 건수만 단언한다).

**수백만 건이면 바꿀 것** (설계, 아직 구현하지 않음)

| 영역 | 지금 | 바꿀 것 |
|---|---|---|
| 오류 행 건너뛰기 | 행마다 `fn_row_has_error`로 검증 결과를 조회 | 묶음의 ERROR 키를 임시 테이블로 한 번에 뽑아 조인 |
| 백필 | 묶음 크기 고정 | 묶음 처리 시간과 레거시 쓰기 지연을 보고 묶음 크기·쉬는 시간을 조절 |
| 변경 기록·검증 결과 | 한 테이블에 쌓임 | 월 단위 파티션, 반영 끝난 변경은 파티션 삭제로 정리 |
| 대조 | 전수 CRC32 | 원본 키 구간별 지문으로 나눠 불일치 구간만 다시 비교 |
| 인물 | 이름으로 만든 인물 | TMDB·KOBIS 인물 ID 매칭 배치로 동명이인 분리 |

---

## 구성

| 구분 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, MyBatis XML Mapper, Flyway, Spring RestClient(TMDB) |
| 프론트엔드 | React 19, TypeScript 5.9, Vite 7, TanStack Query 5, React Router 7, Recharts 3, pnpm |
| DB | MySQL 8 (프로시저·함수·트리거, JSON_TABLE, 재귀 CTE) |
| 테스트 | JUnit 5, Testcontainers MySQL, Mockito(TMDB) |
| CI | GitHub Actions |

```text
dummy/                 서버 없이 여는 HTML·CSS·JS 버전 (더미 데이터)
├── index.html         운영 화면 데모
├── demo/              화면 데모의 app.js·app.css·data.js
└── guide/flow.html    백엔드 흐름 시뮬레이터
backend/src/main
├── java/com/sleekydz86/contentmeta
│   ├── migration   이관 작업, 사전 검증·백필·동기화·대조 프로시저 호출, 읽기 전환
│   ├── operation   배치 실행 기록·SLA·품질 점검, 정기 배치
│   ├── catalog     신규 메타 조회(페이지·외부 ID로 찾기), 외부 ID·현지화 편집
│   ├── dashboard   대시보드·통계·배치 추이 집계
│   ├── tmdb        TMDB 조회·후보 검색·반영
│   └── global      설정, 오류, 운영 토큰, 프로시저 공통(rtn_cod·err_msg, 작업자)
└── resources/db
    ├── legacy      V0.1 구시스템 테이블, V0.2 샘플, V0.3 부하 데이터 생성 (실습용)
    ├── migration   V1 스키마 ~ V10 TMDB 반영 프로시저
    └── demo        R__ 데모 작품(영화 9·TV 10)과 긴 줄거리 (demo 프로필에서만)
frontend/src
├── api             응답 타입·호출 함수 (/api 는 Vite가 8086으로 넘긴다)
├── components      레이아웃·표·배지·페이지네이션·차트
└── pages           대시보드·작품 목록·상세·통계·모니터링·이관
```

### 프로시저

| 파일 | 프로시저·함수 | 하는 일 |
|---|---|---|
| V2 | `fn_map_code`, `fn_try_date`, `fn_try_runtime` | 레거시 값 → 표준 코드, 문자열 날짜·상영 시간 해석 (잘못된 값은 오류 대신 NULL) |
| V4 | `sp_validate_legacy_migration`, `sp_validate_legacy_keys` | 필수값·형식·중복·코드 대응·외부 ID 검사 → ERROR/INFO |
| V5 | `sp_backfill_legacy_chunk`, `sp_upsert_legacy_keys` | 체크포인트 묶음 적재, 묶음마다 커밋, 실패하면 롤백 후 FAILED |
| V6 | `trg_*` 9개, `sp_sync_legacy_changes` | 변경 키 기록, 변경분 동기화(SKIP LOCKED) |
| V7 | `sp_verify_legacy_migration` | 누락·불일치·고아 대조 |
| V8 | `sp_batch_run_start`·`finish`, `sp_run_quality_checks`, `sp_check_sla_freshness` | 배치 겹침 방지·SLA, 품질 규칙 |
| V9 | `sp_external_id_cud`, `sp_localization_cud` | 운영자 편집, `rtn_cod`·`err_msg`, `work_log` |
| V10 | `sp_title_tmdb_apply` | TMDB 포스터·IMDB·영어 현지화 반영 (지문 컬럼·MANUAL·다른 작품의 ID는 건드리지 않음) |

## 실행 방법

### 1. MySQL

이미 MySQL 8이 있으면 DB와 계정을 만들고, 함수·트리거를 만들 수 있게 한다.

```sql
CREATE DATABASE content_meta DEFAULT CHARSET utf8mb4;
CREATE USER 'app'@'%' IDENTIFIED BY 'app';
GRANT ALL ON content_meta.* TO 'app'@'%';
SET GLOBAL log_bin_trust_function_creators = 1;  -- 바이너리 로그가 켜져 있고 SUPER 권한이 없을 때
```

없으면 `docker compose up -d`로 3307에 띄운다.

### 2. 백엔드

```bash
cd backend
# 데모 데이터 + 기본 토큰(demo-admin-token)으로 띄우기
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
# TMDB까지: TMDB_API_KEY=<v3 키 또는 v4 읽기 토큰> SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
# docker compose MySQL이면 DB_URL=jdbc:mysql://localhost:3307/content_meta 를 앞에 붙인다
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="demo"; $env:TMDB_API_KEY="..."; ./gradlew bootRun
```

Swagger: http://localhost:8086/swagger-ui.html

### 2-1. 화면 (pnpm)

```bash
cd frontend
pnpm install
pnpm dev          # http://localhost:5173 (/api → localhost:8086)
```

오른쪽 위 **운영 토큰**에 `demo-admin-token`(또는 띄울 때 넣은 ADMIN_TOKEN)을 넣는다.
처음에는 읽기 원천이 LEGACY라 작품 목록이 막혀 있다 → **이관** 화면에서 BACKFILL 작업 → 사전 검증 → 백필 → VERIFY 작업 → 대조 → NEW로 전환하면 열린다.

### 3. 이관 한 바퀴 (운영 API는 `X-Admin-Token` 헤더가 필요하다)

```bash
H="X-Admin-Token: local-token"
curl -X POST -H "$H" "localhost:8086/api/admin/migration/runs?mode=BACKFILL&batchSize=500"   # runId=1
curl -X POST -H "$H" "localhost:8086/api/admin/migration/runs/1/precheck"
curl -H "$H" "localhost:8086/api/admin/migration/runs/1/issues?severity=ERROR"
curl -X POST -H "$H" "localhost:8086/api/admin/migration/runs/1/backfill"                   # done=true까지
curl -X POST -H "$H" "localhost:8086/api/admin/operation/sync"                              # 레거시 변경분
curl -X POST -H "$H" "localhost:8086/api/admin/migration/runs?mode=VERIFY"                  # runId=2
curl -X POST -H "$H" "localhost:8086/api/admin/migration/runs/2/verify"
curl -X POST -H "$H" "localhost:8086/api/admin/migration/read-source?target=NEW"
curl "localhost:8086/api/catalog/by-external/TMDB_TV/1399"
curl -X POST -H "$H" "localhost:8086/api/admin/operation/quality-checks"
```

### 4. 테스트

```bash
cd backend
./gradlew test   # Docker가 필요하다 (Testcontainers MySQL)
```

| 테스트 | 확인하는 것 |
|---|---|
| `LegacyMigrationScenarioTest` | 사전 검증, 묶음 백필·재실행, 변경분 동기화·대조·읽기 전환, 실패 후 재개 |
| `OperationIntegrationTest` | 품질 FAIL 판정, 배치 겹침 방지, SLA 위반·실패 기록, 동기화 배치 |
| `CatalogEditIntegrationTest` | 외부 ID 중복·형식 거절, 인물 확인, 운영자 현지화 보호 |
| `BackfillLoadTest` | 2만5천 건 백필 뒤 대조 0/0/0 |
| `CatalogPageIntegrationTest` | 읽기 전환 전 차단, 페이지 건수, 유형·애니·장르·검색어 필터, 정렬 |
| `DashboardIntegrationTest` | 읽기 전환 가능 여부, 동기화 대기, 통계 집계, 품질 결과·배치 추이 |
| `TmdbIntegrationTest` | 키 없음·미연결 응답, 캐시, 반영 뒤 동기화가 포스터를 지우지 않고 대조 0/0/0 |
| `AdminTokenInterceptorTest` | 운영 토큰 |

화면: `cd frontend && pnpm typecheck && pnpm lint && pnpm build`

## 아직 하지 못한 것

- **외부 ID 자동 매칭 배치**: 화면에서 TMDB 후보를 찾아 운영자가 연결하는 것까지만 있다. 제목 + 연도 + 감독 점수로 후보를 미리 뽑아 두는 배치, KOBIS 연동은 없다
- **TMDB 시즌·에피소드 수집**: TV 시리즈는 시즌이 비어 있다 (DQ005로 보인다)
- **축소 단계**: 레거시 화면을 새 API로 옮기고 트리거·레거시 테이블을 지우는 단계는 하지 않았다
- **운영 경험**: 실제 트래픽에서 백필이 레거시 쓰기 지연에 주는 영향은 재지 않았다
