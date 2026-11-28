import { LeftOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, DatePicker, Empty, Select, Space, Typography } from 'antd'
import type { Dayjs } from 'dayjs'
import { useMemo, useState } from 'react'
import { operationApi } from '@/backoffice/api/operation'
import type { ScheduleSlot } from '@/backoffice/api/types'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { seoul, toIso, weekStart } from '@/backoffice/utils/time'
import { DraftModal } from './DraftModal'
import { SlotDrawer } from './SlotDrawer'
import { WeekTimeline } from './WeekTimeline'

export function SchedulePage() {
  const { can, scopes } = usePermission()
  const [channel, setChannel] = useState<string | undefined>(undefined)
  const [week, setWeek] = useState<Dayjs>(() => weekStart(seoul()))
  const [selected, setSelected] = useState<ScheduleSlot | null>(null)
  const [draftAt, setDraftAt] = useState<Dayjs | null>(null)

  const channels = useQuery({ queryKey: ['channels'], queryFn: operationApi.channels, staleTime: 300_000 })
  const myChannels = useMemo(
    () => (channels.data ?? []).filter((c) => scopes.length === 0 || scopes.includes(c.code)),
    [channels.data, scopes],
  )
  const channelCode = channel ?? myChannels[0]?.code
  const current = myChannels.find((c) => c.code === channelCode)

  const from = toIso(week)
  const to = toIso(week.add(7, 'day'))
  const slots = useQuery({
    queryKey: ['schedule-slots', channelCode, from],
    queryFn: () => operationApi.slots({ channelCode, from, to }),
    enabled: !!channelCode,
  })

  return (
    <Card
      title="편성"
      extra={
        can('schedule:draft') && (
          <Button type="primary" icon={<PlusOutlined />} disabled={!channelCode} onClick={() => setDraftAt(week.add(20, 'hour'))}>
            초안 작성
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 200 }}
          placeholder="채널"
          loading={channels.isLoading}
          value={channelCode}
          onChange={setChannel}
          options={myChannels.map((c) => ({ value: c.code, label: `${c.name} (${c.code})` }))}
        />
        <Button icon={<LeftOutlined />} onClick={() => setWeek(week.subtract(7, 'day'))} />
        <DatePicker picker="week" value={week} allowClear={false} onChange={(d) => d && setWeek(weekStart(d))} />
        <Button icon={<RightOutlined />} onClick={() => setWeek(week.add(7, 'day'))} />
        <Button onClick={() => setWeek(weekStart(seoul()))}>이번 주</Button>
        {current?.minRatingAfter22 && (
          <Typography.Text type="secondary">22시 이후 최소 등급: {current.minRatingAfter22}</Typography.Text>
        )}
      </Space>

      {channels.error && <Alert type="error" message={errorMessage(channels.error)} />}
      {slots.error && <Alert type="error" message={errorMessage(slots.error)} style={{ marginBottom: 12 }} />}
      {!channels.isLoading && myChannels.length === 0 ? (
        <Empty description="다룰 수 있는 채널이 없습니다" />
      ) : (
        <WeekTimeline
          week={week}
          slots={slots.data?.data ?? []}
          loading={slots.isFetching}
          onSelect={setSelected}
          onEmptyClick={can('schedule:draft') ? setDraftAt : undefined}
        />
      )}

      {selected && <SlotDrawer slot={selected} onClose={() => setSelected(null)} />}
      {draftAt && channelCode && (
        <DraftModal channelCode={channelCode} minRatingAfter22={current?.minRatingAfter22} startAt={draftAt} onClose={() => setDraftAt(null)} onCreated={() => void slots.refetch()} />
      )}
    </Card>
  )
}
