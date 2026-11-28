
export const titleTypeLabel: Record<string, string> = {
  MOVIE: '영화',
  SERIES: '시리즈',
  MOVIE_ANIMATION: '애니메이션 영화',
  SERIES_ANIMATION: '애니메이션 시리즈',
}

export const titleStatusLabel: Record<string, string> = {
  RELEASED: '공개됨',
  RETURNING: '방영 중',
  ENDED: '완결',
  PLANNED: '공개 예정',
  CANCELED: '중단',
  DELETED: '원본 삭제',
}

export const runStatusLabel: Record<string, string> = {
  CREATED: '대기',
  RUNNING: '실행 중',
  COMPLETED: '완료',
  COMPLETED_WITH_ISSUES: '완료(검증 오류)',
  FAILED: '실패',
}

export const modeLabel: Record<string, string> = {
  BACKFILL: '백필',
  SYNC: '변경분 동기화',
  VERIFY: '대조',
}

export const jobLabel: Record<string, string> = {
  LEGACY_SYNC: '변경분 동기화',
  LEGACY_VERIFY: '원본·신규 대조',
  QUALITY_CHECK: '데이터 품질 점검',
}

export const roleLabel: Record<string, string> = {
  DIRECTOR: '감독',
  CREATOR: '원작·기획',
  WRITER: '각본',
  CAST: '출연',
}

export const issueTypeLabel: Record<string, string> = {
  REQUIRED_MISSING: '필수값 없음',
  INVALID_FORMAT: '형식 오류',
  CODE_MAPPING_FAILED: '코드 매핑 실패',
  DUPLICATE_SOURCE_KEY: '원본 키 중복',
  DUPLICATE_EXTERNAL_ID: '외부 ID 중복',
  EXTERNAL_ID_MISSING: '외부 ID 없음',
  EPISODE_RANGE_INVALID: '회차 범위 오류',
}

export const sourceTableLabel: Record<string, string> = {
  movies: '영화 (movies)',
  MergedTVShow: 'TV (MergedTVShow)',
  cartoon_video: '애니메이션 (cartoon_video)',
}

export const providerLabel: Record<string, string> = {
  TMDB_MOVIE: 'TMDB 영화',
  TMDB_TV: 'TMDB TV',
  TMDB_PERSON: 'TMDB 인물',
  IMDB: 'IMDb',
  KOBIS: 'KOBIS',
}

export function providerLink(provider: string, id: string): string | null {
  switch (provider) {
    case 'TMDB_MOVIE': return `https://www.themoviedb.org/movie/${id}`
    case 'TMDB_TV': return `https://www.themoviedb.org/tv/${id}`
    case 'TMDB_PERSON': return `https://www.themoviedb.org/person/${id}`
    case 'IMDB': return id.startsWith('nm') ? `https://www.imdb.com/name/${id}/` : `https://www.imdb.com/title/${id}/`
    default: return null
  }
}

export const label = (map: Record<string, string>, code: string | null | undefined) => (code ? map[code] ?? code : '-')
