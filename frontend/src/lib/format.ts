const numberFormat = new Intl.NumberFormat('ko-KR')

export const fmtNumber = (value: number | null | undefined) => (value == null ? '-' : numberFormat.format(value))

export function fmtPercent(part: number, whole: number): string {
  if (!whole) return '-'
  return `${((part / whole) * 100).toFixed(1)}%`
}

/** 'YYYY-MM-DD' 그대로 (백엔드 LocalDate) */
export const fmtDate = (value: string | null | undefined) => value ?? '-'

/** LocalDateTime 'YYYY-MM-DDTHH:mm:ss.SSS' → 'MM-DD HH:mm:ss' */
export function fmtDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const [date = '', time = ''] = value.split('T')
  return `${date.slice(5)} ${time.slice(0, 8)}`
}

export function fmtDuration(startedAt: string | null, finishedAt: string | null): string {
  if (!startedAt || !finishedAt) return '-'
  const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime()
  if (Number.isNaN(ms)) return '-'
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

export function fmtMinutes(minutes: number | null | undefined): string {
  if (minutes == null) return '-'
  if (minutes < 60) return `${minutes}분`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`
  return `${Math.floor(minutes / 1440)}일`
}

/** TMDB 포스터 경로(/abc.jpg)면 이미지 주소로, 그 외(레거시 전체 주소)는 그대로 */
export function posterUrl(path: string | null, width: 92 | 185 | 342 = 185): string | null {
  if (!path) return null
  if (path.startsWith('http')) return path
  if (path.startsWith('/')) return `https://image.tmdb.org/t/p/w${width}${path}`
  return null
}
