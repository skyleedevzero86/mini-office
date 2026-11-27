import { apiUrl } from './config'
import { session, type Session, type SessionUser } from './session'

export class ApiError extends Error {
  readonly statusCode: number
  readonly code: string
  readonly traceId: string | null

  constructor(statusCode: number, code: string, message: string, traceId: string | null) {
    super(message)
    this.name = 'ApiError'
    this.statusCode = statusCode
    this.code = code
    this.traceId = traceId
  }
}

export type Query = Record<string, string | number | boolean | null | undefined>

export interface RequestOptions {
  query?: Query
  body?: unknown
  rawBody?: string
  headers?: Record<string, string>
  idempotencyKey?: string
  signal?: AbortSignal
}

export interface RawResponse<T> {
  status: number
  headers: Headers
  data: T
}

interface TokenBody {
  accessToken: string
  refreshToken: string
  user: SessionUser
}

const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const NO_REFRESH = ['/api/auth/login', '/api/auth/refresh']

export function withQuery(path: string, query?: Query): string {
  if (!query) return path
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const qs = params.toString()
  if (!qs) return path
  return path.includes('?') ? `${path}&${qs}` : `${path}?${qs}`
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export function toSession(body: TokenBody): Session {
  const u = body.user
  return {
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
    user: { id: u.id, email: u.email, name: u.name, team: u.team, roles: u.roles, perms: u.perms, scopes: u.scopes },
  }
}

let refreshing: Promise<boolean> | null = null

export function refreshSession(): Promise<boolean> {
  if (refreshing) return refreshing
  const current = session.get()
  if (!current) return Promise.resolve(false)
  refreshing = (async () => {
    try {
      const res = await fetch(apiUrl('/api/auth/refresh'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ refreshToken: current.refreshToken }),
      })
      if (!res.ok) {
        session.clear()
        return false
      }
      session.set(toSession((await res.json()) as TokenBody))
      return true
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

export function authHeaders(method: string, idempotencyKey?: string): Record<string, string> {
  const headers: Record<string, string> = {}
  const token = session.get()?.accessToken
  if (token) headers.Authorization = `Bearer ${token}`
  if (WRITE_METHODS.has(method)) headers['Idempotency-Key'] = idempotencyKey ?? newIdempotencyKey()
  return headers
}

export async function toApiError(res: Response): Promise<ApiError> {
  const text = await res.text().catch(() => '')
  let body: { code?: string; message?: string; traceId?: string } = {}
  try {
    body = text ? JSON.parse(text) : {}
  } catch {
    body = {}
  }
  const traceId = body.traceId ?? res.headers.get('X-Trace-Id')
  return new ApiError(res.status, body.code ?? `HTTP_${res.status}`, body.message ?? statusMessage(res.status), traceId)
}

function statusMessage(status: number): string {
  if (status === 401) return '로그인이 필요합니다.'
  if (status === 403) return '권한이 없습니다.'
  if (status === 404) return '찾을 수 없습니다.'
  if (status === 409) return '다른 사람이 먼저 바꿨습니다. 새로 불러온 뒤 다시 시도하세요.'
  if (status >= 500) return '서버 오류입니다. 잠시 뒤 다시 시도하세요.'
  return `요청 실패 (${status})`
}

export async function rawRequest<T>(method: string, path: string, options: RequestOptions = {}): Promise<RawResponse<T>> {
  const upper = method.toUpperCase()
  const key = WRITE_METHODS.has(upper) ? (options.idempotencyKey ?? newIdempotencyKey()) : undefined
  const body = options.rawBody ?? (options.body === undefined ? undefined : JSON.stringify(options.body))
  const url = apiUrl(withQuery(path, options.query))

  const send = () => {
    const headers: Record<string, string> = { Accept: 'application/json', ...options.headers, ...authHeaders(upper, key) }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    return fetch(url, { method: upper, headers, body, signal: options.signal })
  }

  let res: Response
  try {
    res = await send()
    if (res.status === 401 && !NO_REFRESH.some((p) => path.startsWith(p)) && (await refreshSession())) {
      res = await send()
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결하지 못했습니다.', null)
  }

  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return { status: res.status, headers: res.headers, data: null as T }
  const text = await res.text()
  return { status: res.status, headers: res.headers, data: (text ? JSON.parse(text) : null) as T }
}

async function request<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
  return (await rawRequest<T>(method, path, options)).data
}

export const http = {
  get: <T>(path: string, query?: Query, options?: RequestOptions) => request<T>('GET', path, { ...options, query }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('POST', path, { ...options, body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('PUT', path, { ...options, body }),
  list: async <T>(path: string, query?: Query): Promise<{ data: T[]; total: number }> => {
    const res = await rawRequest<T[]>('GET', path, { query })
    const total = Number(res.headers.get('x-total-count'))
    return { data: res.data ?? [], total: Number.isFinite(total) && total > 0 ? total : (res.data?.length ?? 0) }
  },
}
