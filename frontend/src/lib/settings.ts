const TOKEN_KEY = 'cm.adminToken'
const WORKER_KEY = 'cm.worker'

const memory = new Map<string, string>()

function read(key: string): string {
  try {
    return window.localStorage.getItem(key) ?? memory.get(key) ?? ''
  } catch {
    return memory.get(key) ?? ''
  }
}

function write(key: string, value: string) {
  memory.set(key, value)
  try {
    if (value) window.localStorage.setItem(key, value)
    else window.localStorage.removeItem(key)
  } catch {
    return
  }
}

export const settings = {
  adminToken: () => read(TOKEN_KEY),
  worker: () => read(WORKER_KEY) || 'console',
  save(adminToken: string, worker: string) {
    write(TOKEN_KEY, adminToken.trim())
    write(WORKER_KEY, worker.trim())
  },
}
