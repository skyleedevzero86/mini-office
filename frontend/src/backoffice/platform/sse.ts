import { apiUrl } from './config'
import { ApiError, authHeaders, refreshSession, toApiError } from './http'

export interface SseMessage {
  event: string
  data: string
}

export function createSseParser(onMessage: (msg: SseMessage) => void) {
  let buffer = ''
  let event = ''
  let data: string[] = []

  const dispatch = () => {
    if (data.length > 0) onMessage({ event: event || 'message', data: data.join('\n') })
    event = ''
    data = []
  }

  const line = (raw: string) => {
    if (raw === '') return dispatch()
    if (raw.startsWith(':')) return
    const idx = raw.indexOf(':')
    const field = idx === -1 ? raw : raw.slice(0, idx)
    let value = idx === -1 ? '' : raw.slice(idx + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }

  return {
    push(chunk: string) {
      buffer += chunk
      let nl: number
      while ((nl = buffer.search(/\r\n|\r|\n/)) !== -1) {
        const sepLen = buffer.startsWith('\r\n', nl) ? 2 : 1
        line(buffer.slice(0, nl))
        buffer = buffer.slice(nl + sepLen)
      }
    },
    end() {
      if (buffer) line(buffer)
      buffer = ''
      dispatch()
    },
  }
}

export interface StreamOptions {
  body: unknown
  signal?: AbortSignal
  onMessage: (msg: SseMessage) => void
}

export async function postSse(path: string, { body, signal, onMessage }: StreamOptions): Promise<void> {
  const send = () =>
    fetch(apiUrl(path), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...authHeaders('POST') },
      body: JSON.stringify(body),
      signal,
    })

  let res = await send()
  if (res.status === 401 && (await refreshSession())) res = await send()
  if (!res.ok) throw await toApiError(res)
  if (!res.body) throw new ApiError(res.status, 'NO_STREAM', '응답 스트림이 없습니다.', res.headers.get('X-Trace-Id'))

  const parser = createSseParser(onMessage)
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader()
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      parser.push(value)
    }
    parser.end()
  } finally {
    reader.releaseLock()
  }
}
