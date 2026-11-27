import type { DataProvider } from '@refinedev/core'
import simpleRest from '@refinedev/simple-rest'
import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { rawRequest } from './http'

async function fetchAdapter(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  const headers: Record<string, string> = {}
  for (const [k, v] of Object.entries(config.headers.toJSON())) {
    const lower = k.toLowerCase()
    if (lower === 'accept' || lower === 'content-type' || v === undefined || v === null) continue
    headers[k] = String(v)
  }
  const rawBody = typeof config.data === 'string' ? config.data : config.data === undefined ? undefined : JSON.stringify(config.data)
  const res = await rawRequest<unknown>(config.method ?? 'get', config.url ?? '', {
    rawBody,
    headers,
    signal: config.signal instanceof AbortSignal ? config.signal : undefined,
  })
  const responseHeaders: Record<string, string> = {}
  res.headers.forEach((value, key) => {
    responseHeaders[key] = value
  })
  return { data: res.data, status: res.status, statusText: '', headers: responseHeaders, config }
}

export const httpClient = axios.create({ adapter: fetchAdapter })

export function gatewayDataProvider(basePath: string): DataProvider {
  return simpleRest(basePath, httpClient)
}

export function createDataProviders<K extends string>(paths: Record<'default' | K, string>): Record<'default' | K, DataProvider> {
  const out = {} as Record<'default' | K, DataProvider>
  for (const name of Object.keys(paths) as Array<'default' | K>) out[name] = gatewayDataProvider(paths[name])
  return out
}
