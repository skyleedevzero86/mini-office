import type { AuthProvider } from '@refinedev/core'
import { ApiError, http, rawRequest, toSession } from './http'
import { decodeJwt } from './jwt'
import { session, type SessionUser } from './session'
import { boPath } from '../routes'

interface LoginParams {
  email: string
  password: string
}

interface TokenBody {
  accessToken: string
  refreshToken: string
  user: SessionUser
}

function errorOf(e: unknown): Error {
  if (e instanceof ApiError) return { name: e.code, message: e.message }
  return { name: 'LOGIN_FAILED', message: '로그인하지 못했습니다.' }
}

export const authProvider: AuthProvider = {
  login: async ({ email, password }: LoginParams) => {
    try {
      const body = await http.post<TokenBody>('/api/auth/login', { email, password })
      session.set(toSession(body))
      return { success: true, redirectTo: boPath('/') }
    } catch (e) {
      return { success: false, error: errorOf(e) }
    }
  },

  logout: async () => {
    const current = session.get()
    if (current) {
      await rawRequest('POST', '/api/auth/logout', { body: { refreshToken: current.refreshToken } }).catch(() => undefined)
    }
    session.clear()
    return { success: true, redirectTo: boPath('/login') }
  },

  check: async () => {
    const current = session.get()
    if (!current) return { authenticated: false, redirectTo: boPath('/login'), logout: true }
    return { authenticated: true }
  },

  onError: async (error: { statusCode?: number }) => {
    if (error?.statusCode === 401) {
      session.clear()
      return { logout: true, redirectTo: boPath('/login') }
    }
    return {}
  },

  getIdentity: async () => session.get()?.user ?? null,

  getPermissions: async () => currentPerms(),
}

export function currentPerms(): string[] {
  const current = session.get()
  if (!current) return []
  const claims = decodeJwt(current.accessToken)
  return claims?.perms ?? current.user.perms
}

export function currentRoles(): string[] {
  const current = session.get()
  if (!current) return []
  return decodeJwt(current.accessToken)?.roles ?? current.user.roles
}

export function currentScopes(): string[] {
  const current = session.get()
  if (!current) return []
  return decodeJwt(current.accessToken)?.scopes ?? current.user.scopes
}
