import { useCallback, useSyncExternalStore } from 'react'
import { hasPerm, type PermRule } from './accessControlProvider'
import { currentPerms, currentRoles, currentScopes } from './authProvider'
import { session, type SessionUser } from './session'

let lastSession: unknown
let snapshot: { user: SessionUser | null; perms: string[]; roles: string[]; scopes: string[] } = {
  user: null, perms: [], roles: [], scopes: [],
}

function getSnapshot() {
  const current = session.get()
  if (current !== lastSession) {
    lastSession = current
    snapshot = { user: current?.user ?? null, perms: currentPerms(), roles: currentRoles(), scopes: currentScopes() }
  }
  return snapshot
}

export function usePermission() {
  const state = useSyncExternalStore(session.subscribe, getSnapshot, getSnapshot)
  const can = useCallback((rule: PermRule) => hasPerm(state.perms, rule), [state.perms])
  const isMe = useCallback(
    (userId: string | number | null | undefined) => state.user !== null && userId !== null && userId !== undefined && String(userId) === String(state.user.id),
    [state.user],
  )
  return { ...state, can, isMe }
}
