import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, App, DatePicker, Form, InputNumber, Modal, Select, Space, Typography } from 'antd'
import type { Dayjs } from 'dayjs'
import { useState } from 'react'
import { contentApi } from '@/backoffice/api/content'
import { operationApi } from '@/backoffice/api/operation'
import type { Conflict, TitleListItem } from '@/backoffice/api/types'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { label, ratingCodes, ratingLabel, titleTypeLabel } from '@/backoffice/utils/labels'
import { DRAFT_LEAD_HOURS, earliestDraftStart, seoul, toIso } from '@/backoffice/utils/time'

interface Props {
  readonly channelCode: string
  readonly minRatingAfter22?: string | null
  readonly startAt: Dayjs
  readonly onClose: () => void
  readonly onCreated: () => void
}

interface DraftForm {
  contentId: number
  episodeId?: number | null
  contentRating?: string
  range: [Dayjs, Dayjs]
}

const MAX_HOURS = 24

function titleOption(t: TitleListItem) {
  const year = t.releaseDate ? ` · ${t.releaseDate.slice(0, 4)}` : ''
  return { value: t.titleId, label: `#${t.titleId} ${t.displayTitle} (${label(titleTypeLabel, t.titleType)}${year})` }
}

export function DraftModal({ channelCode, minRatingAfter22, startAt, onClose, onCreated }: Props) {
  const { message } = App.useApp()
  const { can } = usePermission()
  const canSearch = can('content:read')
  const [form] = Form.useForm<DraftForm>()
  const [conflicts, setConflicts] = useState<Conflict[]>([])
  const [query, setQuery] = useState('')
  const [picked, setPicked] = useState<TitleListItem | null>(null)
  const start = startAt.isBefore(earliestDraftStart()) ? earliestDraftStart() : startAt

  const titles = useQuery({
    queryKey: ['content', 'titles', 'draft', query],
    queryFn: () => contentApi.page({ query: query || undefined, page: 0, size: 20 }),
    enabled: canSearch,
    staleTime: 30_000,
  })

  const mutation = useMutation({
    mutationFn: async (v: DraftForm) => {
      const [from, to] = v.range
      const rating = v.contentRating || null
      const found = await operationApi.conflicts({ channelCode, contentId: v.contentId, contentRating: rating, startAt: toIso(from), endAt: toIso(to) })
      if (found.length > 0) return { conflicts: found }
      await operationApi.createDraft({
        channelCode,
        contentId: v.contentId,
        episodeId: v.episodeId ?? null,
        contentRating: rating,
        startAt: toIso(from),
        endAt: toIso(to),
      })
      return { conflicts: [] as Conflict[] }
    },
    onSuccess: ({ conflicts: found }) => {
      setConflicts(found)
      if (found.length > 0) return
      message.success('초안을 저장했습니다.')
      onCreated()
      onClose()
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const options = (titles.data?.items ?? []).map(titleOption)
  if (picked && !options.some((o) => o.value === picked.titleId)) options.unshift(titleOption(picked))

  return (
    <Modal
      open
      title={`편성 초안 작성 (${channelCode})`}
      okText="초안 저장"
      cancelText="닫기"
      confirmLoading={mutation.isPending}
      onCancel={onClose}
      onOk={() => form.validateFields().then((v) => mutation.mutate(v))}
      destroyOnHidden
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">
          시작까지 {DRAFT_LEAD_HOURS}시간 넘게 남은 시각만 초안으로 저장할 수 있습니다. 시간 겹침, 비노출 작품, 22시 이후 등급을 저장 전에 확인합니다.
        </Typography.Text>
        {conflicts.length > 0 && (
          <Alert
            type="warning"
            message="충돌이 있어 저장하지 않았습니다"
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {conflicts.map((c, i) => (
                  <li key={`${c.type}-${c.slotId ?? i}`}>{c.message}</li>
                ))}
              </ul>
            }
          />
        )}
        <Form<DraftForm>
          form={form}
          layout="vertical"
          initialValues={{ range: [start, start.add(1, 'hour')] }}
          onValuesChange={() => conflicts.length > 0 && setConflicts([])}
        >
          <Form.Item name="contentId" label="작품" rules={[{ required: true, message: '작품을 골라 주세요.' }]}>
            {canSearch ? (
              <Select
                showSearch
                placeholder="제목으로 찾기"
                filterOption={false}
                onSearch={(v) => setQuery(v.trim())}
                loading={titles.isFetching}
                notFoundContent={titles.error ? errorMessage(titles.error) : '찾는 작품이 없습니다'}
                options={options}
                onChange={(id: number) => setPicked(titles.data?.items.find((t) => t.titleId === id) ?? picked)}
              />
            ) : (
              <InputNumber min={1} style={{ width: '100%' }} placeholder="작품 번호" />
            )}
          </Form.Item>
          {picked?.titleType !== 'MOVIE' && (
            <Form.Item name="episodeId" label="회차 번호" extra="시리즈를 회차 단위로 편성할 때만 적습니다.">
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item
            name="contentRating"
            label="관람 등급"
            extra={minRatingAfter22 ? `이 채널은 22시 이후 ${label(ratingLabel, minRatingAfter22)} 작품만 편성할 수 있습니다.` : undefined}
          >
            <Select allowClear placeholder="등급" options={ratingCodes.map((c) => ({ value: c, label: ratingLabel[c] }))} />
          </Form.Item>
          <Form.Item
            name="range"
            label="방송 시간"
            rules={[
              { required: true, message: '시간을 골라 주세요.' },
              {
                validator: (_, v?: [Dayjs, Dayjs]) => {
                  if (!v) return Promise.resolve()
                  const [from, to] = v
                  if (from.isBefore(seoul().add(DRAFT_LEAD_HOURS, 'hour'))) {
                    return Promise.reject(new Error(`시작은 지금부터 ${DRAFT_LEAD_HOURS}시간 뒤 이후여야 합니다.`))
                  }
                  if (!to.isAfter(from)) return Promise.reject(new Error('끝나는 시각은 시작보다 늦어야 합니다.'))
                  if (to.diff(from, 'hour', true) > MAX_HOURS) return Promise.reject(new Error('편성 한 건은 24시간을 넘을 수 없습니다.'))
                  return Promise.resolve()
                },
              },
            ]}
          >
            <DatePicker.RangePicker
              showTime={{ format: 'HH:mm', minuteStep: 10 }}
              format="YYYY-MM-DD HH:mm"
              style={{ width: '100%' }}
              disabledDate={(d) => d.isBefore(earliestDraftStart(), 'day')}
            />
          </Form.Item>
        </Form>
      </Space>
    </Modal>
  )
}
