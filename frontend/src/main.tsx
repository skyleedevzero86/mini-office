import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import { ApiError } from './api/client'
import { ToastProvider } from './components/Toast'
import './styles.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 업무 오류(4xx)는 다시 불러도 같으므로 재시도하지 않는다
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 1,
      refetchOnWindowFocus: false,
      staleTime: 10_000,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <App />
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
)
