import { http, withQuery } from '@/backoffice/platform'
import type { Approval, ApprovalCreateCommand, ApprovalStatus, AuditExport, AuditFilter, AuditLog, AutomationCandidate, ToolPolicy, ToolPolicyCommand } from './types'

const G = '/api/governance'

export const governanceApi = {
  toolPolicies: () => http.get<ToolPolicy[]>(`${G}/tool-policies`),
  updateToolPolicy: (toolName: string, command: ToolPolicyCommand) =>
    http.put<ToolPolicy>(`${G}/tool-policies/${encodeURIComponent(toolName)}`, command),

  approvals: (q: { status?: ApprovalStatus; mine?: boolean; _start: number; _end: number; _order?: 'asc' | 'desc' }) =>
    http.list<Approval>(`${G}/approvals`, { ...q, _sort: 'createdAt' }),
  approval: (id: number) => http.get<Approval>(`${G}/approvals/${id}`),
  pendingCount: async () => (await http.list<Approval>(`${G}/approvals`, { status: 'PENDING', _start: 0, _end: 1 })).total,
  requestApproval: (command: ApprovalCreateCommand) => http.post<Approval>(`${G}/approvals`, command),
  approve: (id: number, comment?: string) => http.post<Approval>(`${G}/approvals/${id}/approve`, { comment }),
  reject: (id: number, comment: string) => http.post<Approval>(`${G}/approvals/${id}/reject`, { comment }),

  auditLogs: (f: AuditFilter, page: { _start: number; _end: number; _order?: 'asc' | 'desc' }) =>
    http.list<AuditLog>(`${G}/audit-logs`, { ...f, ...page, _sort: 'createdAt' }),
  exportAudit: (f: AuditFilter) => http.post<AuditExport>(withQuery(`${G}/audit-logs/export`, { ...f })),
  auditTrace: async (traceId: string) =>
    (await http.list<AuditLog>(`${G}/audit-logs`, { traceId, _start: 0, _end: 100, _sort: 'createdAt', _order: 'asc' })).data,
  recentAgentRuns: async () =>
    (await http.list<AuditLog>(`${G}/audit-logs`, { actorType: 'AGENT', _start: 0, _end: 5, _sort: 'createdAt', _order: 'desc' })).data,
  automationCandidates: (days = 7, limit = 10) => http.get<AutomationCandidate[]>(`${G}/automation-candidates`, { days, limit }),
}
