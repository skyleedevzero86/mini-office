import { http, postSse, type SseMessage } from '@/backoffice/platform'
import type { AgentTool, Conversation, Message } from './types'

const A = '/api/agent'

export const agentApi = {
  conversations: () => http.get<Conversation[]>(`${A}/conversations`),
  createConversation: (title?: string) => http.post<Conversation>(`${A}/conversations`, { title }),
  messages: (conversationId: string) => http.get<Message[]>(`${A}/conversations/${conversationId}/messages`),
  tools: () => http.get<AgentTool[]>(`${A}/tools`),
  send: (conversationId: string, content: string, onMessage: (msg: SseMessage) => void, signal?: AbortSignal) =>
    postSse(`${A}/conversations/${conversationId}/messages`, { body: { content }, onMessage, signal }),
}

export interface TokenEvent { text: string }
export interface ToolCallEvent { callId: string; name: string; arguments: unknown }
export interface ToolResultEvent { callId: string; name: string; ok: boolean; code?: string; message?: string; approvalId?: number }
export interface ApprovalPendingEvent { approvalId: number; toolName: string; reason: string }
export interface DoneEvent { messageId: number; inputTokens: number; outputTokens: number; toolCalls: number }
export interface ErrorEvent { code: string; message: string }

export type AgentEvent =
  | { event: 'token'; data: TokenEvent }
  | { event: 'tool_call'; data: ToolCallEvent }
  | { event: 'tool_result'; data: ToolResultEvent }
  | { event: 'approval_pending'; data: ApprovalPendingEvent }
  | { event: 'done'; data: DoneEvent }
  | { event: 'error'; data: ErrorEvent }

const KNOWN = new Set(['token', 'tool_call', 'tool_result', 'approval_pending', 'done', 'error'])

export function parseAgentEvent(msg: SseMessage): AgentEvent | null {
  if (!KNOWN.has(msg.event)) return null
  try {
    return { event: msg.event, data: JSON.parse(msg.data) } as AgentEvent
  } catch {
    return null
  }
}
