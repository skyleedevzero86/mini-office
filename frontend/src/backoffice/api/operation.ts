import { http } from '@/backoffice/platform'
import type {
  Channel, Conflict, Exposure, Issue, IssueCreateCommand, IssueDetail, OperationSummary, ScheduleSlot, SlotDraftCommand, SlotStatus,
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
  startAt: string
  endAt: string
  excludeSlotId?: number
}

export const operationApi = {
  channels: () => http.get<Channel[]>(`${O}/channels`),
  slots: (q: SlotQuery) => http.list<ScheduleSlot>(`${O}/schedule-slots`, { ...q, _start: 0, _end: 100, _sort: 'startAt', _order: 'asc' }),
  slot: (id: number) => http.get<ScheduleSlot>(`${O}/schedule-slots/${id}`),
  conflicts: (q: ConflictQuery) => http.get<Conflict[]>(`${O}/schedule-slots/conflicts`, { ...q }),
  createDraft: (command: SlotDraftCommand) => http.post<ScheduleSlot>(`${O}/schedule-slots`, command),

  issue: (id: number) => http.get<IssueDetail>(`${O}/issues/${id}`),
  createIssue: (command: IssueCreateCommand) => http.post<Issue>(`${O}/issues`, command),
  assign: (id: number, assigneeId: number) => http.post<Issue>(`${O}/issues/${id}/assign`, { assigneeId }),
  comment: (id: number, body: string) => http.post<unknown>(`${O}/issues/${id}/comments`, { body }),
  resolve: (id: number, resolution: string) => http.post<Issue>(`${O}/issues/${id}/resolve`, { resolution }),

  exposure: (contentId: number) => http.get<Exposure>(`${O}/exposures/${contentId}`),
  summary: () => http.get<OperationSummary>(`${O}/summary`),
}
