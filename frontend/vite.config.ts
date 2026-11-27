import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// 백엔드(Spring Boot, 기본 8086)로 /api를 넘긴다. 다른 주소면 .env.local에 VITE_API_TARGET을 적는다
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxy = {
    '/api': { target: env.VITE_API_TARGET || 'http://localhost:8086', changeOrigin: true },
  }
  return {
    plugins: [react()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: { port: 5173, proxy },
    preview: { port: 4173, proxy },
    build: {
      rollupOptions: {
        output: { manualChunks: { charts: ['recharts'], vendor: ['react', 'react-dom', 'react-router', '@tanstack/react-query'] } },
      },
    },
  }
})
