import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

interface ChatDrawerState {
  open: boolean
  setOpen: (open: boolean) => void
  toggle: () => void
}

const ChatDrawerContext = createContext<ChatDrawerState | null>(null)

export function ChatDrawerProvider({ children }: { readonly children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const value = useMemo(() => ({ open, setOpen, toggle: () => setOpen((v) => !v) }), [open])
  return <ChatDrawerContext.Provider value={value}>{children}</ChatDrawerContext.Provider>
}

export function useChatDrawer(): ChatDrawerState {
  const ctx = useContext(ChatDrawerContext)
  if (!ctx) throw new Error('ChatDrawerProvider 안에서만 쓴다')
  return ctx
}
