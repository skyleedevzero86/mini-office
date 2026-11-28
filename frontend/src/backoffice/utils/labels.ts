
export const severityLabel: Record<string, string> = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '긴급' }
export const severityColor: Record<string, string> = { LOW: 'default', MEDIUM: 'blue', HIGH: 'orange', CRITICAL: 'red' }

export const issueTypeLabel: Record<string, string> = { PLAYBACK_ERROR: '재생 오류', META_ERROR: '메타 오류', RIGHTS: '권리 문제' }
export const issueStatusLabel: Record<string, string> = { OPEN: '열림', ASSIGNED: '배정됨', RESOLVED: '해결' }
export const issueStatusColor: Record<string, string> = { OPEN: 'red', ASSIGNED: 'blue', RESOLVED: 'green' }

export const slotStatusLabel: Record<string, string> = { DRAFT: '초안', PUBLISHED: '게시', CANCELED: '취소' }
export const slotStatusColor: Record<string, string> = { DRAFT: 'gold', PUBLISHED: 'green', CANCELED: 'default' }

export const conflictLabel: Record<string, string> = {
  TIME_OVERLAP: '시간 겹침',
  CONTENT_HIDDEN: '비노출 콘텐츠',
  RATING_TIME_BAND: '등급-시간대 위반',
}

export const approvalStatusLabel: Record<string, string> = {
  PENDING: '대기',
  APPROVED: '승인됨',
  REJECTED: '반려',
  EXPIRED: '만료',
  EXECUTED: '실행 완료',
  FAILED: '실행 실패',
}
export const approvalStatusColor: Record<string, string> = {
  PENDING: 'gold', APPROVED: 'blue', REJECTED: 'default', EXPIRED: 'default', EXECUTED: 'green', FAILED: 'red',
}

export const riskLabel: Record<string, string> = { READ: '조회', WRITE_LOW: '변경(즉시)', WRITE_HIGH: '변경(승인)' }
export const riskColor: Record<string, string> = { READ: 'default', WRITE_LOW: 'blue', WRITE_HIGH: 'red' }

export const actorTypeLabel: Record<string, string> = { HUMAN: '사람', AGENT: '에이전트', SYSTEM: '시스템' }
export const actorTypeColor: Record<string, string> = { HUMAN: 'blue', AGENT: 'purple', SYSTEM: 'default' }

export const auditResultLabel: Record<string, string> = { SUCCESS: '성공', DENIED: '거부', PENDING: '승인 대기', FAILED: '실패' }
export const auditResultColor: Record<string, string> = { SUCCESS: 'green', DENIED: 'red', PENDING: 'gold', FAILED: 'red' }

export const titleTypeLabel: Record<string, string> = { MOVIE: '영화', SERIES: '시리즈' }

export const ratingLabel: Record<string, string> = { ALL: '전체 관람가', '12': '12세 이상', '15': '15세 이상', '19': '19세 이상' }
export const ratingCodes = ['ALL', '12', '15', '19'] as const

export const toolLabel: Record<string, string> = {
  content_link_external_id: '외부 ID 연결',
  content_update_localization: '현지화 수정',
  content_apply_tmdb: 'TMDB 반영',
  schedule_create_draft: '편성 초안 작성',
  schedule_publish_slot: '편성 게시',
  schedule_cancel_slot: '편성 취소',
  exposure_hide_content: '콘텐츠 비노출',
  exposure_restore_content: '비노출 해제',
  issue_create: '이슈 등록',
  issue_assign: '이슈 배정',
  issue_add_comment: '이슈 댓글',
  issue_resolve: '이슈 해결',
}

export const argLabel: Record<string, string> = {
  slotId: '편성 슬롯',
  contentId: '콘텐츠 ID',
  titleId: '작품 ID',
  issueId: '이슈 ID',
  channelCode: '채널',
  startAt: '시작',
  endAt: '종료',
  provider: '제공처',
  externalId: '외부 ID',
  reason: '사유',
}

export function label(map: Record<string, string>, code: string | null | undefined): string {
  if (!code) return '-'
  return map[code] ?? code
}
