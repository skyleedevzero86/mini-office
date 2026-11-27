
export interface SessionUser {
  id: number
  email: string
  name: string
  team: string | null
  roles: string[]
  perms: string[]
  scopes: string[]
}

export interface Session {
  accessToken: string
  refreshToken: string
  user: SessionUser
}

const KEY = 'aibo.session'
const listeners = new Set<() => void>()
let cached: Session | null | undefined

function read(): Session | null {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export const session = {
  get(): Session | null {
    if (cached === undefined) cached = read()
    return cached
  },
  set(next: Session | null) {
    cached = next
    try {
      if (next) localStorage.setItem(KEY, JSON.stringify(next))
      else localStorage.removeItem(KEY)
    } catch (error) {
      console.warn('로그인 정보를 브라우저에 저장하지 못했습니다.', error)
    }
    listeners.forEach((fn) => fn())
  },
  clear() {
    session.set(null)
  },
  subscribe(fn: () => void) {
    listeners.add(fn)
    return () => {
      listeners.delete(fn)
    }
  },
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key !== KEY) return
    cached = read()
    listeners.forEach((fn) => fn())
  })
}
