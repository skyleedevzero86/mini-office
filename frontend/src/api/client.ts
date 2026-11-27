import type { ApiErrorBody } from './types'
import { settings } from '@/lib/settings'

/** 백엔드 오류 응답(code·message·detail)을 그대로 담는다 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly detail: string | null

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.status = status
    this.code = body.code
    this.detail = body.detail
  }
}

type Query = Record<string, string | number | boolean | null | undefined>

function withQuery(path: string, query?: Query): string {
  if (!query) return path
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${path}?${qs}` : path
}

async function request<T>(method: string, path: string, options: { query?: Query; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (path.startsWith('/api/admin/')) {
    headers['X-Admin-Token'] = settings.adminToken()
    headers['X-Worker'] = encodeURIComponent(settings.worker())
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(withQuery(path, options.query), {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (response.status === 204) return null as T
  const text = await response.text()
  const data: unknown = text ? JSON.parse(text) : null
  if (!response.ok) {
    const body = (data ?? {}) as Partial<ApiErrorBody>
    throw new ApiError(response.status, {
      code: body.code ?? `HTTP_${response.status}`,
      message: body.message ?? (response.status === 401 ? '운영 토큰이 필요합니다.' : `요청 실패 (${response.status})`),
      detail: body.detail ?? null,
    })
  }
  return data as T
}

export const http = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, { query }),
  post: <T>(path: string, query?: Query, body?: unknown) => request<T>('POST', path, { query, body }),
}
