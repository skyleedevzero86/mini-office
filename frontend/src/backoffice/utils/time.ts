import dayjs, { type Dayjs } from 'dayjs'
import 'dayjs/locale/ko'
import timezone from 'dayjs/plugin/timezone'
import utc from 'dayjs/plugin/utc'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.locale('ko')

export const TZ = 'Asia/Seoul'

export const seoul = (value?: string | number | Date | Dayjs) => (value === undefined ? dayjs().tz(TZ) : dayjs(value).tz(TZ))

export const toIso = (value: Dayjs) => value.tz(TZ).format('YYYY-MM-DDTHH:mm:ssZ')

export function fmtDateTime(value: string | null | undefined): string {
  return value ? seoul(value).format('YYYY-MM-DD HH:mm') : '-'
}

export function fmtDate(value: string | null | undefined): string {
  return value ? seoul(value).format('YYYY-MM-DD') : '-'
}

export function fmtTime(value: string | null | undefined): string {
  return value ? seoul(value).format('HH:mm') : '-'
}

export function weekStart(value: Dayjs): Dayjs {
  const d = value.tz(TZ).startOf('day')
  const offset = (d.day() + 6) % 7
  return d.subtract(offset, 'day')
}
