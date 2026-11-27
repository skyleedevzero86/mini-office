import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { ApiError } from '@/api/client'

interface ToastItem {
  id: number
  tone: 'ok' | 'bad'
  title: string
  detail?: string | null
}

interface ToastApi {
  ok: (title: string, detail?: string | null) => void
  error: (error: unknown) => void
}

const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])

  const push = useCallback((item: Omit<ToastItem, 'id'>) => {
    const id = Date.now() + Math.random()
    setItems((prev) => [...prev, { ...item, id }])
    setTimeout(() => setItems((prev) => prev.filter((t) => t.id !== id)), 5000)
  }, [])

  const api = useMemo<ToastApi>(
    () => ({
      ok: (title, detail) => push({ tone: 'ok', title, detail }),
      error: (error) => {
        if (error instanceof ApiError) push({ tone: 'bad', title: `[${error.code}] ${error.message}`, detail: error.detail })
        else push({ tone: 'bad', title: error instanceof Error ? error.message : '요청에 실패했습니다.' })
      },
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.tone === 'bad' ? 'bad' : ''}`}>
            {t.title}
            {t.detail && <small>{t.detail}</small>}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const api = useContext(ToastContext)
  if (!api) throw new Error('ToastProvider가 필요합니다.')
  return api
}
