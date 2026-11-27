
export interface UserView {
  id: number
  email: string
  name: string
  team: string | null
  status: 'ACTIVE' | 'LOCKED'
  roles: string[]
  perms: string[]
  scopes: string[]
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserView
}

export interface RoleView {
  code: string
  name: string
  permissions: string[]
}

export type SlotStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELED'
export type IssueType = 'PLAYBACK_ERROR' | 'META_ERROR' | 'RIGHTS'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IssueStatus = 'OPEN' | 'ASSIGNED' | 'RESOLVED'

export interface Channel {
  id: number
  code: string
  name: string
  minRatingAfter22: string | null
}

export interface ScheduleSlot {
  id: number
  channelCode: string
  contentId: number
  episodeId: number | null
  contentRating: string | null
  startAt: string
  endAt: string
  status: SlotStatus
  approvalId: number | null
  createdBy: string
  version: number
  createdAt: string
}

export interface Conflict {
  type: 'TIME_OVERLAP' | 'CONTENT_HIDDEN' | 'RATING_TIME_BAND'
  message: string
  slotId: number | null
}

export interface SlotDraftCommand {
  channelCode: string
  contentId: number
  episodeId?: number | null
  contentRating?: string | null
  startAt: string
  endAt: string
}

export interface Issue {
  id: number
  type: IssueType
  severity: Severity
  status: IssueStatus
  contentId: number | null
  title: string
  description: string
  reporter: string
  assigneeId: number | null
  createdAt: string
  resolvedAt: string | null
}

export interface IssueComment {
  id: number
  author: string
  source: 'HUMAN' | 'AGENT'
  body: string
  createdAt: string
}

export type IssueDetail = Issue & { comments: IssueComment[] }

export interface IssueCreateCommand {
  type: IssueType
  severity: Severity
  contentId?: number | null
  title: string
  description: string
}

export interface Exposure {
  contentId: number
  exposed: boolean
  reason: string | null
  issueId: number | null
  approvalId: number | null
  updatedBy: string | null
  updatedAt: string | null
}

export interface OperationSummary {
  todaySlots: number
  draftSlots: number
  openIssuesBySeverity: Record<Severity, number>
  hiddenContents: number
}

export type RiskLevel = 'READ' | 'WRITE_LOW' | 'WRITE_HIGH'
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'EXECUTED' | 'FAILED'
export type ActorType = 'HUMAN' | 'AGENT' | 'SYSTEM'
export type AuditResult = 'SUCCESS' | 'DENIED' | 'PENDING' | 'FAILED'

export interface ToolPolicy {
  toolName: string
  domain: string
  riskLevel: RiskLevel
  requiredPermission: string
  enabled: boolean
  approvalTtlMinutes: number
  updatedAt: string
}

export interface ToolPolicyCommand {
  riskLevel: RiskLevel
  requiredPermission: string
  enabled: boolean
  approvalTtlMinutes: number
}

export interface Approval {
  id: number
  toolName: string
  arguments: Record<string, unknown>
  riskLevel: string
  requesterId: string
  requesterName: string | null
  requesterType: 'HUMAN' | 'AGENT'
  conversationId: string | null
  reason: string | null
  scopeChannel: string | null
  status: ApprovalStatus
  approverId: string | null
  decisionComment: string | null
  decidedAt: string | null
  expiresAt: string
  result: Record<string, unknown> | null
  traceId: string | null
  createdAt: string
}

export interface ApprovalCreateCommand {
  toolName: string
  arguments: Record<string, unknown>
  reason: string
  conversationId?: string
}

export interface AuditLog {
  id: number
  traceId: string | null
  actorType: ActorType
  actorId: string
  onBehalfOf: string | null
  service: string
  action: string
  resourceType: string | null
  resourceId: string | null
  beforeValue: unknown
  afterValue: unknown
  result: AuditResult
  reason: string | null
  approvalId: number | null
  createdAt: string
}

export interface AutomationCandidate {
  action: string
  count: number
  actorCount: number
  lastAt: string
  suggestion: string
}

export interface Conversation {
  id: string
  title: string
  createdAt: string
}

export interface ToolInvocation {
  toolName: string
  arguments: unknown
  status: 'OK' | 'DENIED' | 'PENDING_APPROVAL' | 'ERROR'
  approvalId: number | null
  latencyMs: number
  errorCode: string | null
}

export interface Message {
  id: number
  role: 'USER' | 'ASSISTANT' | 'TOOL'
  content: string
  model: string | null
  inputTokens: number | null
  outputTokens: number | null
  createdAt: string
  toolInvocations: ToolInvocation[]
}

export interface AgentTool {
  name: string
  description?: string
  [key: string]: unknown
}

export type TitleType = 'MOVIE' | 'SERIES'
export type Provider = 'TMDB_MOVIE' | 'TMDB_TV' | 'TMDB_PERSON' | 'IMDB' | 'KOBIS'

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

export interface TitleDetail {
  title: TitleRow
  externalIds: ExternalIdRow[]
  localizations: LocalizationRow[]
  genres: string[]
  countries: string[]
}

export interface TmdbTitle {
  configured: boolean
  linked: boolean
  provider: 'TMDB_MOVIE' | 'TMDB_TV' | null
  tmdbId: number | null
  title: string | null
  originalTitle: string | null
  overview: string | null
  posterUrl: string | null
  releaseDate: string | null
  tmdbUrl: string | null
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

export interface ContentOverview {
  readSource: string
  counts: { totalTitles: number; titlesWithExternalId: number; titlesWithKo: number; pendingChanges: number }
  latestQuality: { jobRunId: number; failedRules: number; totalRules: number } | null
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

export interface QualityDetail {
  jobRunId: number
  rules: QualityResultRow[]
}
