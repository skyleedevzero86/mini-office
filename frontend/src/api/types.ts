
export type ReadSource = 'LEGACY' | 'NEW'
export type MigrationMode = 'BACKFILL' | 'SYNC' | 'VERIFY'
export type RunStatus = 'CREATED' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ISSUES' | 'FAILED'
export type TitleType = 'MOVIE' | 'SERIES'
export type TitleSort = 'RELEASE_DESC' | 'RELEASE_ASC' | 'TITLE_ASC' | 'RATING_DESC' | 'UPDATED_DESC'
export type BatchJob = 'LEGACY_SYNC' | 'LEGACY_VERIFY' | 'QUALITY_CHECK'
export type Provider = 'TMDB_MOVIE' | 'TMDB_TV' | 'TMDB_PERSON' | 'IMDB' | 'KOBIS'

export interface ApiErrorBody {
  code: string
  message: string
  detail: string | null
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface TitleListItem {
  titleId: number
  titleType: TitleType
  displayTitle: string
  originalTitle: string
  releaseDate: string | null
  status: string
  animation: boolean
  voteAverage: number | null
  posterPath: string | null
  seasonCount: number
  providers: Provider[]
  genres: string[]
}

export interface GenreRow {
  genreCode: string
  nameKo: string
  titleCount: number
}

export interface TitleRow {
  titleId: number
  titleType: TitleType
  originalTitle: string
  originalLanguage: string | null
  releaseDate: string | null
  runtimeMin: number | null
  status: string
  animation: boolean
  popularity: number | null
  voteAverage: number | null
  voteCount: number | null
  posterPath: string | null
  metaVersion: number
}

export interface ExternalIdRow {
  provider: Provider
  externalId: string
  matchMethod: string
  verified: boolean
}

export interface LocalizationRow {
  langCode: string
  localTitle: string
  overview: string | null
  source: string
}

export interface RegionRow {
  regionCode: string
  releaseDate: string | null
  ageRating: string | null
  source: string
}

export interface SeasonRow {
  seasonId: number
  seasonNo: number
  seasonName: string | null
  airDate: string | null
  episodeCount: number | null
  loadedEpisodes: number
}

export interface CreditRow {
  personId: number
  personName: string
  roleType: 'CAST' | 'DIRECTOR' | 'WRITER' | 'CREATOR'
  characterName: string
  creditOrder: number
  nameOnly: boolean
}

export interface TitleDetail {
  title: TitleRow
  externalIds: ExternalIdRow[]
  localizations: LocalizationRow[]
  regions: RegionRow[]
  genres: string[]
  countries: string[]
  seasons: SeasonRow[]
  credits: CreditRow[]
}

export interface ExternalIdCommand {
  operation: 'C' | 'U' | 'D'
  entityType: 'TITLE' | 'SEASON' | 'EPISODE' | 'PERSON'
  entityId: number
  provider: Provider
  externalId: string | null
}

export interface LocalizationCommand {
  operation: 'C' | 'U' | 'D'
  titleId: number
  langCode: string
  localTitle: string | null
  overview: string | null
}

export interface MigrationRunRow {
  runId: number
  mode: MigrationMode
  status: RunStatus
  description: string | null
  batchSize: number
  lastMovieSeq: number
  lastTvSeq: number
  lastCartoonSeq: number
  batchCount: number
  attemptCount: number
  errorCount: number
  infoCount: number
  upsertedTitles: number
  upsertedCredits: number
  upsertedEpisodes: number
  errorMessage: string | null
  createdAt: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface IssueRow {
  issueId: number
  sourceTable: string
  sourceKey: string
  issueType: string
  severity: 'ERROR' | 'INFO'
  message: string
}

export interface IssueSummaryRow {
  sourceTable: string
  issueType: string
  severity: 'ERROR' | 'INFO'
  cnt: number
}

export interface PrecheckResult {
  runId: number
  errorCount: number
  infoCount: number
}

export interface BackfillResult {
  runId: number
  chunks: number
  titles: number
  errors: number
  done: boolean
  elapsedMs: number
}

export interface SyncResult {
  runId: number
  changes: number
  titles: number
  deleted: number
  errors: number
}

export interface VerifyTableRow {
  sourceTable: string
  sourceCount: number
  targetCount: number
  missingCount: number
  mismatchCount: number
  orphanCount: number
}

export interface VerifyResult {
  runId: number
  missing: number
  mismatch: number
  orphan: number
  tables: VerifyTableRow[]
  clean: boolean
}

export interface LatestVerifyRow {
  runId: number
  finishedAt: string | null
  ageMinutes: number
  missing: number
  mismatch: number
  orphan: number
}

export interface BatchOutcome {
  job: BatchJob
  jobRunId: number | null
  skipped: boolean
  success: boolean
  processed: number
  slaBreached: boolean
  message: string | null
}

export interface QualityResultRow {
  ruleCode: string
  ruleName: string
  severity: 'HIGH' | 'LOW'
  violationCount: number
  threshold: number
  result: 'PASS' | 'FAIL'
  sampleKeys: string | null
}

export interface QualityReport {
  outcome: BatchOutcome
  failedRules: number
  rules: QualityResultRow[]
}

export interface BatchRunRow {
  jobRunId: number
  jobName: BatchJob
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  startedAt: string | null
  finishedAt: string | null
  processedCount: number
  errorMessage: string | null
  slaBreached: boolean
  worker: string
}

export interface SlaFreshnessRow {
  jobName: BatchJob
  maxGapMinutes: number
  lastSuccessAt: string | null
  minutesSinceSuccess: number | null
  failures24h: number
  slaBreaches24h: number
  freshness: 'OK' | 'STALE'
}

export interface OverviewCounts {
  totalTitles: number
  movieCount: number
  seriesCount: number
  animationCount: number
  deletedCount: number
  seasonCount: number
  episodeCount: number
  personCount: number
  nameOnlyPersonCount: number
  titlesWithExternalId: number
  titlesWithKo: number
  legacyRows: number
  pendingChanges: number
  oldestPendingChangeAt: string | null
}

export interface DashboardOverview {
  readSource: ReadSource
  counts: OverviewCounts
  latestVerify: LatestVerifyRow | null
  readSwitchAllowed: boolean
  latestQuality: { jobRunId: number; failedRules: number; totalRules: number } | null
  sla: SlaFreshnessRow[]
}

export interface LabelCount {
  label: string
  cnt: number
}

export interface CatalogStatistics {
  byType: LabelCount[]
  byGenre: LabelCount[]
  byReleaseYear: LabelCount[]
  byStatus: LabelCount[]
  byProvider: LabelCount[]
  byLanguage: LabelCount[]
  issuesByType: LabelCount[]
}

export interface BatchTrendRow {
  runDate: string
  jobName: BatchJob
  successCount: number
  failedCount: number
  slaBreachedCount: number
  avgDurationMs: number | null
}

export interface VerifyDetail {
  latest: LatestVerifyRow
  tables: VerifyTableRow[]
}

export interface QualityDetail {
  jobRunId: number
  rules: QualityResultRow[]
}

export interface TmdbStatus {
  configured: boolean
  language: string
}

export interface TmdbPerson {
  tmdbPersonId: number
  name: string
  character: string | null
  profileUrl: string | null
}

export interface TmdbTitle {
  configured: boolean
  linked: boolean
  provider: 'TMDB_MOVIE' | 'TMDB_TV' | null
  tmdbId: number | null
  title: string | null
  originalTitle: string | null
  overview: string | null
  tagline: string | null
  posterPath: string | null
  posterUrl: string | null
  backdropUrl: string | null
  releaseDate: string | null
  runtime: number | null
  voteAverage: number | null
  voteCount: number | null
  genres: string[]
  directors: string[]
  cast: TmdbPerson[]
  imdbId: string | null
  homepage: string | null
  tmdbUrl: string | null
  status: string | null
  seasons: number | null
  episodes: number | null
  networks: string[]
}

export interface TmdbCandidate {
  provider: 'TMDB_MOVIE' | 'TMDB_TV'
  tmdbId: number
  title: string | null
  originalTitle: string | null
  date: string | null
  posterUrl: string | null
  overview: string | null
  voteAverage: number | null
  linkedTitleId: number | null
}

export interface TmdbApplyResult {
  titleId: number
  note: string
  posterPath: string | null
  imdbId: string | null
}
