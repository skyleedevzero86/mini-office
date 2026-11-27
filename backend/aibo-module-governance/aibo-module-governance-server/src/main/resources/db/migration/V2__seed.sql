-- =====================================================================================
-- Flyway 마이그레이션: 기준 데이터
-- 프로젝트: AI Backoffice Platform (ai-backoffice-platform)
-- 마이그레이션 버전: V2
-- 작성일: 2026-11-26
-- 설명: 이 서비스가 처음 뜰 때 필요한 기준 데이터를 넣는다
-- =====================================================================================

-- =====================================================================================
-- 1. 툴 정책 (tool_policies)
-- 설명: 설계 문서 6장 표의 툴 26개. 위험도와 필요 권한은 그 표와 같아야 한다.
--       승인을 거쳐야 하는 툴은 하루 동안 승인을 기다린다.
--       approval:read:self 는 실제 권한 코드가 아니다. 자기 요청은 누구나 보고,
--       남의 요청까지 보려면 approval:decide 가 있어야 한다는 뜻으로 툴 실행기가 읽는다
-- =====================================================================================
INSERT INTO tool_policies (tool_name, domain, risk_level, required_permission, enabled, approval_ttl_minutes) VALUES
    ('content_search_titles',          'content',    'READ',       'content:read',       1, 0),
    ('content_get_title',              'content',    'READ',       'content:read',       1, 0),
    ('content_find_by_external_id',    'content',    'READ',       'content:read',       1, 0),
    ('content_search_tmdb_candidates', 'content',    'READ',       'content:read',       1, 0),
    ('content_link_external_id',       'content',    'WRITE_HIGH', 'content:write',      1, 1440),
    ('content_update_localization',    'content',    'WRITE_LOW',  'content:write',      1, 0),
    ('content_apply_tmdb',             'content',    'WRITE_LOW',  'content:write',      1, 0),
    ('content_get_quality_report',     'content',    'READ',       'content:read',       1, 0),
    ('content_get_migration_status',   'content',    'READ',       'content:read',       1, 0),
    ('schedule_list_slots',            'schedule',   'READ',       'schedule:read',      1, 0),
    ('schedule_find_conflicts',        'schedule',   'READ',       'schedule:read',      1, 0),
    ('schedule_create_draft',          'schedule',   'WRITE_LOW',  'schedule:draft',     1, 0),
    ('schedule_publish_slot',          'schedule',   'WRITE_HIGH', 'schedule:publish',   1, 1440),
    ('schedule_cancel_slot',           'schedule',   'WRITE_HIGH', 'schedule:publish',   1, 1440),
    ('issue_search',                   'issue',      'READ',       'issue:read',         1, 0),
    ('issue_get',                      'issue',      'READ',       'issue:read',         1, 0),
    ('issue_create',                   'issue',      'WRITE_LOW',  'issue:write',        1, 0),
    ('issue_assign',                   'issue',      'WRITE_LOW',  'issue:write',        1, 0),
    ('issue_add_comment',              'issue',      'WRITE_LOW',  'issue:write',        1, 0),
    ('issue_resolve',                  'issue',      'WRITE_LOW',  'issue:write',        1, 0),
    ('exposure_hide_content',          'issue',      'WRITE_HIGH', 'exposure:write',     1, 1440),
    ('exposure_restore_content',       'issue',      'WRITE_HIGH', 'exposure:write',     1, 1440),
    ('approval_list_pending',          'governance', 'READ',       'approval:read:self', 1, 0),
    ('approval_get',                   'governance', 'READ',       'approval:read:self', 1, 0),
    ('audit_search',                   'governance', 'READ',       'audit:read',         1, 0),
    ('automation_list_candidates',     'governance', 'READ',       'audit:read',         1, 0);

