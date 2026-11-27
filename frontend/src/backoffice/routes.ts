export const BO_BASE = '/bo'

export const boPath = (path: string) => (path === '/' ? BO_BASE : `${BO_BASE}${path}`)
