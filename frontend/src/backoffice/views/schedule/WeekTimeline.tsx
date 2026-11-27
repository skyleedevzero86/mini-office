import { Spin, Tooltip } from 'antd'
import type { Dayjs } from 'dayjs'
import type { ScheduleSlot } from '@/backoffice/api/types'
import { label, slotStatusLabel } from '@/backoffice/utils/labels'
import { fmtTime, seoul } from '@/backoffice/utils/time'
import './timeline.css'

const HOUR_PX = 28
const DAY_NAMES = ['월', '화', '수', '목', '금', '토', '일']

interface Props {
  readonly week: Dayjs
  readonly slots: ScheduleSlot[]
  readonly loading: boolean
  readonly onSelect: (slot: ScheduleSlot) => void
  readonly onEmptyClick?: (at: Dayjs) => void
}

export function WeekTimeline({ week, slots, loading, onSelect, onEmptyClick }: Props) {
  const days = Array.from({ length: 7 }, (_, i) => week.add(i, 'day'))
  const today = seoul().format('YYYY-MM-DD')

  return (
    <Spin spinning={loading}>
      <div className="tl">
        <div className="tl-head">
          <div className="tl-gutter" />
          {days.map((d, i) => (
            <div key={i} className={`tl-day-head${d.format('YYYY-MM-DD') === today ? ' tl-today' : ''}`}>
              {DAY_NAMES[i]} {d.format('M/D')}
            </div>
          ))}
        </div>
        <div className="tl-body">
          <div className="tl-gutter">
            {Array.from({ length: 24 }, (_, h) => (
              <div key={h} className="tl-hour" style={{ height: HOUR_PX }}>
                {String(h).padStart(2, '0')}
              </div>
            ))}
          </div>
          {days.map((day, i) => {
            const dayStart = day.startOf('day')
            const dayEnd = dayStart.add(1, 'day')
            const mine = slots.filter((s) => seoul(s.startAt).isBefore(dayEnd) && seoul(s.endAt).isAfter(dayStart))
            return (
              <div
                key={i}
                className="tl-day"
                style={{ height: HOUR_PX * 24 }}
                onClick={(e) => {
                  if (!onEmptyClick || e.target !== e.currentTarget) return
                  const y = e.nativeEvent.offsetY
                  const minutes = Math.floor(y / HOUR_PX) * 60 + (y % HOUR_PX >= HOUR_PX / 2 ? 30 : 0)
                  onEmptyClick(dayStart.add(minutes, 'minute'))
                }}
              >
                {mine.map((s) => {
                  const start = seoul(s.startAt)
                  const end = seoul(s.endAt)
                  const top = Math.max(0, start.diff(dayStart, 'minute'))
                  const bottom = Math.min(24 * 60, end.diff(dayStart, 'minute'))
                  return (
                    <Tooltip key={s.id} title={`${fmtTime(s.startAt)}~${fmtTime(s.endAt)} · 콘텐츠 ${s.contentId} · ${label(slotStatusLabel, s.status)}`}>
                      <button
                        type="button"
                        className={`tl-slot tl-${s.status.toLowerCase()}`}
                        style={{ top: (top / 60) * HOUR_PX, height: Math.max(14, ((bottom - top) / 60) * HOUR_PX - 2) }}
                        onClick={() => onSelect(s)}
                      >
                        <strong>{fmtTime(s.startAt)}</strong> #{s.contentId}
                        {s.status !== 'PUBLISHED' && <span className="tl-badge">{label(slotStatusLabel, s.status)}</span>}
                      </button>
                    </Tooltip>
                  )
                })}
              </div>
            )
          })}
        </div>
      </div>
    </Spin>
  )
}
