import { useCallback, useEffect, useRef, useState } from 'react'
import { agentApi, parseAgentEvent, type AgentEvent } from '@/backoffice/api/agent'
import type { Message } from '@/backoffice/api/types'
import { errorMessage } from '@/backoffice/platform'

export type ToolStatus = 'RUNNING' | 'OK' | 'DENIED' | 'PENDING_APPROVAL' | 'ERROR'

export type ChatItem =
  | { kind: 'user'; id: string; text: string }
  | { kind: 'assistant'; id: string; text: string; streaming: boolean }
  | {
      kind: 'tool'; id: string; callId: string; name: string; args: unknown; status: ToolStatus
      code?: string; message?: string; approvalId?: number | null; latencyMs?: number
    }
  | { kind: 'approval'; id: string; approvalId: number; toolName: string; reason: string }
  | { kind: 'error'; id: string; code: string; message: string }
  | { kind: 'done'; id: string; inputTokens: number; outputTokens: number; toolCalls: number }

const CONV_KEY = 'aibo.chat.conversation'

let seq = 0
const nextId = () => `c${++seq}`

function readConv(): string | null {
  try {
    return localStorage.getItem(CONV_KEY)
  } catch {
    return null
  }
}

function writeConv(id: string | null) {
  try {
    if (id) localStorage.setItem(CONV_KEY, id)
    else localStorage.removeItem(CONV_KEY)
  } catch (error) {
    console.warn('대화 번호를 브라우저에 저장하지 못했습니다.', error)
  }
}

export function messagesToItems(messages: Message[]): ChatItem[] {
  const items: ChatItem[] = []
  for (const m of messages) {
    if (m.role === 'USER') items.push({ kind: 'user', id: `m${m.id}`, text: m.content })
    if (m.role !== 'ASSISTANT') continue
    m.toolInvocations.forEach((t, i) => {
      items.push({
        kind: 'tool', id: `m${m.id}t${i}`, callId: `m${m.id}t${i}`, name: t.toolName, args: t.arguments,
        status: t.status, code: t.errorCode ?? undefined, approvalId: t.approvalId, latencyMs: t.latencyMs,
      })
      if (t.status === 'PENDING_APPROVAL' && t.approvalId !== null) {
        items.push({ kind: 'approval', id: `m${m.id}a${i}`, approvalId: t.approvalId, toolName: t.toolName, reason: '' })
      }
    })
    if (m.content) items.push({ kind: 'assistant', id: `m${m.id}`, text: m.content, streaming: false })
  }
  return items
}

function finishStreaming(items: ChatItem[]): ChatItem[] {
  return items.map((it) => (it.kind === 'assistant' && it.streaming ? { ...it, streaming: false } : it))
}

function toolStatusOf(ok: boolean, code?: string): ToolStatus {
  if (ok) return 'OK'
  if (code === 'PENDING_APPROVAL') return 'PENDING_APPROVAL'
  if (code === 'PERMISSION_DENIED' || code === 'SCOPE_DENIED') return 'DENIED'
  return 'ERROR'
}

export function applyEvent(items: ChatItem[], ev: AgentEvent): ChatItem[] {
  const last = items[items.length - 1]
  switch (ev.event) {
    case 'token':
      if (last?.kind === 'assistant' && last.streaming) {
        return [...items.slice(0, -1), { ...last, text: last.text + ev.data.text }]
      }
      return [...items, { kind: 'assistant', id: nextId(), text: ev.data.text, streaming: true }]
    case 'tool_call':
      return [
        ...finishStreaming(items),
        { kind: 'tool', id: nextId(), callId: ev.data.callId, name: ev.data.name, args: ev.data.arguments, status: 'RUNNING' },
      ]
    case 'tool_result': {
      const r = ev.data
      return items.map((it) =>
        it.kind === 'tool' && it.callId === r.callId
          ? { ...it, status: toolStatusOf(r.ok, r.code), code: r.code, message: r.message, approvalId: r.approvalId ?? null }
          : it,
      )
    }
    case 'approval_pending':
      return [...items, { kind: 'approval', id: nextId(), approvalId: ev.data.approvalId, toolName: ev.data.toolName, reason: ev.data.reason }]
    case 'done':
      return [...finishStreaming(items), { kind: 'done', id: nextId(), ...ev.data }]
    case 'error':
      return [...finishStreaming(items), { kind: 'error', id: nextId(), code: ev.data.code, message: ev.data.message }]
  }
}

export function useAgentChat() {
  const [conversationId, setConversationId] = useState<string | null>(readConv)
  const [items, setItems] = useState<ChatItem[]>([])
  const [sending, setSending] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const createdRef = useRef<string | null>(null)

  useEffect(() => {
    writeConv(conversationId)
    setLoadError(null)
    if (!conversationId) {
      setItems([])
      return
    }
    if (createdRef.current === conversationId) return
    let alive = true
    agentApi
      .messages(conversationId)
      .then((ms) => {
        if (alive) setItems(messagesToItems(ms))
      })
      .catch((e) => {
        if (alive) setLoadError(errorMessage(e))
      })
    return () => {
      alive = false
    }
  }, [conversationId])

  useEffect(() => () => abortRef.current?.abort(), [])

  const send = useCallback(
    async (content: string) => {
      const text = content.trim()
      if (!text || sending) return
      setSending(true)
      setItems((prev) => [...prev, { kind: 'user', id: nextId(), text }])
      const controller = new AbortController()
      abortRef.current = controller
      try {
        let id = conversationId
        if (!id) {
          const conv = await agentApi.createConversation(text.slice(0, 40))
          id = conv.id
          createdRef.current = id
          setConversationId(id)
        }
        await agentApi.send(
          id,
          text,
          (msg) => {
            const ev = parseAgentEvent(msg)
            if (ev) setItems((prev) => applyEvent(prev, ev))
          },
          controller.signal,
        )
      } catch (e) {
        if (!(e instanceof DOMException && e.name === 'AbortError')) {
          setItems((prev) => applyEvent(prev, { event: 'error', data: { code: 'STREAM_FAILED', message: errorMessage(e) } }))
        }
      } finally {
        setItems(finishStreaming)
        setSending(false)
        abortRef.current = null
      }
    },
    [conversationId, sending],
  )

  const stop = useCallback(() => abortRef.current?.abort(), [])

  const selectConversation = useCallback((id: string | null) => {
    abortRef.current?.abort()
    createdRef.current = null
    setConversationId(id)
  }, [])

  return { conversationId, selectConversation, items, sending, loadError, send, stop }
}
