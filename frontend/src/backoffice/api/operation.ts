import { http } from '@/backoffice/platform'
import type {
  Attachment, Channel, Conflict, Exposure, Issue, IssueCreateCommand, IssueDetail, OperationSummary, ScheduleSlot, SlotDraftCommand, SlotStatus, IssueStatus, IssueType, Severity,
} from './types'

const O = '/api/operation'

export interface SlotQuery {
  channelCode?: string
  from?: string
  to?: string
  status?: SlotStatus
}

export interface ConflictQuery {
  channelCode: string
  contentId: number
  contentRating?: string | null
  startAt: string
  endAt: string
  excludeSlotId?: number
}

export interface IssueQuery {
  type?: IssueType
  severity?: Severity
  status?: IssueStatus
  contentId?: number
  q?: string
  _start: number
  _end: number
  _sort?: string
  _order?: 'asc' | 'desc'
}

export const operationApi = {
  channels: () => http.get<Channel[]>(`${O}/channels`),
  slots: (q: SlotQuery) => http.list<ScheduleSlot>(`${O}/schedule-slots`, { ...q, _start: 0, _end: 100, _sort: 'startAt', _order: 'asc' }),
  slot: (id: number) => http.get<ScheduleSlot>(`${O}/schedule-slots/${id}`),
  conflicts: (q: ConflictQuery) => http.get<Conflict[]>(`${O}/schedule-slots/conflicts`, { ...q }),
  createDraft: (command: SlotDraftCommand) => http.post<ScheduleSlot>(`${O}/schedule-slots`, command),

  issues: (q: IssueQuery) => http.list<Issue>(`${O}/issues`, { ...q }),
  issue: (id: number) => http.get<IssueDetail>(`${O}/issues/${id}`),
  createIssue: (command: IssueCreateCommand) => http.post<Issue>(`${O}/issues`, command),
  assign: (id: number, assigneeId: number) => http.post<Issue>(`${O}/issues/${id}/assign`, { assigneeId }),
  comment: (id: number, body: string) => http.post<unknown>(`${O}/issues/${id}/comments`, { body }),
  resolve: (id: number, resolution: string) => http.post<Issue>(`${O}/issues/${id}/resolve`, { resolution }),

  attachments: (issueId: number) => http.get<Attachment[]>(`${O}/issues/${issueId}/attachments`),
  upload: (issueId: number, file: File) => http.upload<Attachment>(`${O}/issues/${issueId}/attachments`, file),
  exposure: (contentId: number) => http.get<Exposure>(`${O}/exposures/${contentId}`),
  summary: () => http.get<OperationSummary>(`${O}/summary`),
}
