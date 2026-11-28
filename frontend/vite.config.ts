import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

const gatewayPaths = ['/api/auth', '/api/operation', '/api/governance', '/api/agent', '/api/content', '/api/mcp']

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gateway = { target: env.VITE_GATEWAY_TARGET || 'http://localhost:8080', changeOrigin: true }
  const proxy = {
    ...Object.fromEntries(gatewayPaths.map((path) => [path, gateway])),
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
