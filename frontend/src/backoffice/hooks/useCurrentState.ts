import { useQuery } from '@tanstack/react-query'
import { contentApi } from '@/backoffice/api/content'
import { operationApi } from '@/backoffice/api/operation'

function num(v: unknown): number | null {
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN
  return Number.isFinite(n) && n > 0 ? n : null
}

export function useCurrentState(toolName: string, args: Record<string, unknown>, enabled = true) {
  return useQuery({
    queryKey: ['currentState', toolName, args],
    enabled,
    staleTime: 10_000,
    queryFn: async (): Promise<Record<string, unknown> | null> => {
      switch (toolName) {
        case 'schedule_publish_slot':
        case 'schedule_cancel_slot': {
          const id = num(args.slotId)
          if (!id) return null
          const slot = await operationApi.slot(id)
          const after = toolName === 'schedule_publish_slot' ? 'PUBLISHED' : 'CANCELED'
          return {
            status: { before: slot.status, after },
            channelCode: slot.channelCode,
            contentId: slot.contentId,
            startAt: slot.startAt,
            endAt: slot.endAt,
          }
        }
        case 'exposure_hide_content':
        case 'exposure_restore_content': {
          const id = num(args.contentId)
          if (!id) return null
          const exposure = await operationApi.exposure(id)
          const label = (v: boolean) => (v ? '노출' : '비노출')
          return { exposed: { before: label(exposure.exposed), after: label(toolName === 'exposure_restore_content') } }
        }
        case 'content_link_external_id': {
          const id = num(args.titleId) ?? num(args.entityId)
          if (!id || typeof args.provider !== 'string') return null
          const detail = await contentApi.detail(id)
          const found = detail.externalIds.find((x) => x.provider === args.provider)
          return { externalId: found?.externalId ?? null }
        }
        default:
          return null
      }
    },
  })
}
