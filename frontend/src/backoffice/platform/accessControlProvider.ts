import type { AccessControlProvider } from '@refinedev/core'
import { currentPerms } from './authProvider'

export type PermRule = string | string[] | null

export type AccessRules = Record<string, Partial<Record<string, PermRule>>>

export function hasPerm(perms: readonly string[], rule: PermRule | undefined): boolean {
  if (rule === undefined || rule === null) return true
  const need = Array.isArray(rule) ? rule : [rule]
  return need.some((p) => perms.includes(p))
}

export function createAccessControlProvider(rules: AccessRules): AccessControlProvider {
  return {
    can: async ({ resource, action }) => {
      if (!resource) return { can: true }
      const table = rules[resource]
      if (!table) return { can: true }
      const rule = action in table ? table[action] : table.list
      const can = hasPerm(currentPerms(), rule)
      return can ? { can } : { can, reason: '권한이 없습니다.' }
    },
    options: {
      buttons: { enableAccessControl: true, hideIfUnauthorized: true },
      queryOptions: { staleTime: 0, gcTime: 0 },
    },
  }
}
