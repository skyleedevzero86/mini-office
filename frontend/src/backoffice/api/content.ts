import { http } from '@/backoffice/platform'
import type { ContentOverview, PageResponse, QualityDetail, TitleDetail, TitleListItem, TmdbCandidate, TmdbTitle } from './types'

const C = '/api/content'

export interface TitlePageQuery {
  query?: string
  type?: string
  genre?: string
  status?: string
  page?: number
  size?: number
}

export const contentApi = {
  page: (q: TitlePageQuery) => http.get<PageResponse<TitleListItem>>(`${C}/catalog/titles/page`, { ...q }),
  detail: (titleId: number) => http.get<TitleDetail>(`${C}/catalog/titles/${titleId}`),
  tmdb: (titleId: number) => http.get<TmdbTitle>(`${C}/catalog/titles/${titleId}/tmdb`),
  searchTmdb: (type: 'MOVIE' | 'SERIES', query: string, year?: number) =>
    http.get<TmdbCandidate[]>(`${C}/admin/tmdb/search`, { type, query, year }),
  overview: () => http.get<ContentOverview>(`${C}/admin/dashboard/overview`),
  latestQuality: () => http.get<QualityDetail | null>(`${C}/admin/dashboard/quality/latest`),
}
