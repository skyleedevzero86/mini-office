
export interface JwtClaims {
  sub?: string
  name?: string
  roles?: string[]
  perms?: string[]
  scopes?: string[]
  team?: string | null
  exp?: number
}

export function decodeJwt(token: string): JwtClaims | null {
  const part = token.split('.')[1]
  if (!part) return null
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtClaims
  } catch {
    return null
  }
}
