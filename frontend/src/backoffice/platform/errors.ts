import { ApiError } from './http'

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.traceId ? `${e.message} (traceId ${e.traceId})` : e.message
  if (e instanceof Error && e.message) return e.message
  return '요청을 처리하지 못했습니다.'
}
