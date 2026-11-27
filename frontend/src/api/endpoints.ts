import { http } from './client'
import type {
  BackfillResult, BatchJob, BatchOutcome, BatchRunRow, BatchTrendRow, CatalogStatistics, DashboardOverview,
  ExternalIdCommand, GenreRow, IssueRow, IssueSummaryRow, LocalizationCommand, MigrationMode, MigrationRunRow,
  PageResponse, PrecheckResult, QualityDetail, QualityReport, ReadSource, SlaFreshnessRow, SyncResult,
  TitleDetail, TitleListItem, TitleSort, TmdbApplyResult, TmdbCandidate, TmdbStatus, TmdbTitle, VerifyDetail, VerifyResult,
} from './types'

export interface TitlePageQuery {
  query?: string
  type?: string
  genre?: string
  status?: string
  animation?: boolean
  sort?: TitleSort
  page?: number
  size?: number
  lang?: string
}

export const catalogApi = {
  page: (q: TitlePageQuery) => http.get<PageResponse<TitleListItem>>('/api/catalog/titles/page', { ...q }),
  genres: () => http.get<GenreRow[]>('/api/catalog/genres'),
  detail: (titleId: number) => http.get<TitleDetail>(`/api/catalog/titles/${titleId}`),
  byExternal: (provider: string, externalId: string) =>
    http.get<TitleDetail>(`/api/catalog/by-external/${encodeURIComponent(provider)}/${encodeURIComponent(externalId)}`),
  saveExternalId: (command: ExternalIdCommand) => http.post<null>('/api/admin/catalog/external-ids', undefined, command),
  saveLocalization: (command: LocalizationCommand) => http.post<null>('/api/admin/catalog/localizations', undefined, command),
}

export const dashboardApi = {
  overview: () => http.get<DashboardOverview>('/api/admin/dashboard/overview'),
  statistics: () => http.get<CatalogStatistics>('/api/admin/dashboard/statistics'),
  batchTrend: (days: number) => http.get<BatchTrendRow[]>('/api/admin/dashboard/batch-trend', { days }),
  latestVerify: () => http.get<VerifyDetail | null>('/api/admin/dashboard/verify/latest'),
  latestQuality: () => http.get<QualityDetail | null>('/api/admin/dashboard/quality/latest'),
}

const M = '/api/admin/migration'

export const migrationApi = {
  runs: (limit = 50) => http.get<MigrationRunRow[]>(`${M}/runs`, { limit }),
  run: (runId: number) => http.get<MigrationRunRow>(`${M}/runs/${runId}`),
  createRun: (mode: MigrationMode, batchSize: number, description?: string) =>
    http.post<MigrationRunRow>(`${M}/runs`, { mode, batchSize, description }),
  issues: (runId: number, severity?: string, limit = 500) => http.get<IssueRow[]>(`${M}/runs/${runId}/issues`, { severity, limit }),
  issueSummary: (runId: number) => http.get<IssueSummaryRow[]>(`${M}/runs/${runId}/issues/summary`),
  precheck: (runId: number) => http.post<PrecheckResult>(`${M}/runs/${runId}/precheck`),
  backfill: (runId: number, maxChunks: number) => http.post<BackfillResult>(`${M}/runs/${runId}/backfill`, { maxChunks, pauseMs: 0 }),
  sync: (runId: number, batchSize: number) => http.post<SyncResult>(`${M}/runs/${runId}/sync`, { batchSize }),
  verify: (runId: number) => http.post<VerifyResult>(`${M}/runs/${runId}/verify`),
  readSource: () => http.get<{ readSource: ReadSource }>(`${M}/read-source`),
  switchReadSource: (target: ReadSource) => http.post<{ readSource: ReadSource }>(`${M}/read-source`, { target }),
}

const O = '/api/admin/operation'

export const operationApi = {
  runSync: () => http.post<BatchOutcome>(`${O}/sync`),
  runVerify: () => http.post<BatchOutcome>(`${O}/verify`),
  runQuality: () => http.post<QualityReport>(`${O}/quality-checks`),
  batchRuns: (job?: BatchJob, limit = 100) => http.get<BatchRunRow[]>(`${O}/batch-runs`, { job, limit }),
  sla: () => http.get<SlaFreshnessRow[]>(`${O}/sla`),
}

export const tmdbApi = {
  status: () => http.get<TmdbStatus>('/api/catalog/tmdb/status'),
  title: (titleId: number) => http.get<TmdbTitle>(`/api/catalog/titles/${titleId}/tmdb`),
  search: (type: 'MOVIE' | 'SERIES', query: string, year?: number) =>
    http.get<TmdbCandidate[]>('/api/admin/tmdb/search', { type, query, year }),
  apply: (titleId: number) => http.post<TmdbApplyResult>(`/api/admin/tmdb/titles/${titleId}/apply`),
}
