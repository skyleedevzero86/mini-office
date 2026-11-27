import { http } from '@/backoffice/platform'
import type { RoleView, UserView } from './types'

export const authApi = {
  me: () => http.get<UserView>('/api/auth/me'),
  users: () => http.get<UserView[]>('/api/auth/users'),
  roles: () => http.get<RoleView[]>('/api/auth/roles'),
  setRoles: (userId: number, roles: string[]) => http.put<null>(`/api/auth/users/${userId}/roles`, { roles }),
  setScopes: (userId: number, channels: string[]) => http.put<null>(`/api/auth/users/${userId}/scopes`, { channels }),
}
