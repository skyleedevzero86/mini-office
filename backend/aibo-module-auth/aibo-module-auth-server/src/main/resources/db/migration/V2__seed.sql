-- =====================================================================================
-- Flyway 마이그레이션: 기준 데이터
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V2
-- 작성일: 2026-11-26
-- 설명: 이 서비스가 처음 뜰 때 필요한 기준 데이터를 넣는다
-- =====================================================================================

-- =====================================================================================
-- 1. 팀 (teams)
-- 설명: 데모 사용자가 속할 팀 3개
-- =====================================================================================
INSERT INTO teams (name) VALUES ('콘텐츠운영팀'), ('편성팀'), ('서비스운영팀');

-- =====================================================================================
-- 2. 역할 (roles)
-- 설명: 설계 문서 5장의 역할 6개
-- =====================================================================================
INSERT INTO roles (code, name) VALUES
    ('VIEWER', '조회'),
    ('CONTENT_OPERATOR', '콘텐츠 운영'),
    ('SCHEDULER', '편성'),
    ('ISSUE_MANAGER', '이슈 대응'),
    ('APPROVER', '승인'),
    ('ADMIN', '관리자');

-- =====================================================================================
-- 3. 권한 (permissions)
-- 설명: 설계 문서 5장의 권한 코드 11개. 코드 값은 화면과 툴 실행기가 그대로 쓴다
-- =====================================================================================
INSERT INTO permissions (code, description) VALUES
    ('content:read', '작품 조회'),
    ('content:write', '외부 ID·현지화·TMDB 반영'),
    ('schedule:read', '편성 조회'),
    ('schedule:draft', '편성 초안 작성'),
    ('schedule:publish', '편성 게시·취소'),
    ('issue:read', '이슈·노출 상태 조회'),
    ('issue:write', '이슈 등록·배정·댓글·해결'),
    ('exposure:write', '콘텐츠 비노출·해제'),
    ('approval:decide', '승인·반려'),
    ('audit:read', '감사 로그·자동화 후보 조회'),
    ('policy:write', '사용자 권한·툴 정책 관리');

-- =====================================================================================
-- 4. 역할 권한 (role_permissions)
-- 설명: 조회 권한 세 개는 관리자를 뺀 모든 역할이 같이 가진다. 관리자는 두 번째 넣기에서 모든 권한을 받는다
-- =====================================================================================
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN ('content:read', 'schedule:read', 'issue:read')
 WHERE r.code IN ('VIEWER', 'CONTENT_OPERATOR', 'SCHEDULER', 'ISSUE_MANAGER', 'APPROVER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p
    ON (r.code = 'CONTENT_OPERATOR' AND p.code = 'content:write')
    OR (r.code = 'SCHEDULER' AND p.code IN ('schedule:draft', 'schedule:publish'))
    OR (r.code = 'ISSUE_MANAGER' AND p.code IN ('issue:write', 'exposure:write'))
    OR (r.code = 'APPROVER' AND p.code IN ('approval:decide', 'audit:read'))
    OR (r.code = 'ADMIN');

