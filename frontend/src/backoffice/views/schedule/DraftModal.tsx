import { useMutation } from '@tanstack/react-query'
import { Alert, App, DatePicker, Form, Input, InputNumber, Modal, Space } from 'antd'
import type { Dayjs } from 'dayjs'
import { useState } from 'react'
import { operationApi } from '@/backoffice/api/operation'
import type { Conflict } from '@/backoffice/api/types'
import { errorMessage } from '@/backoffice/platform'
import { toIso } from '@/backoffice/utils/time'

interface Props {
  readonly channelCode: string
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

export function DraftModal({ channelCode, startAt, onClose, onCreated }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<DraftForm>()
  const [conflicts, setConflicts] = useState<Conflict[]>([])

  const mutation = useMutation({
    mutationFn: async (v: DraftForm) => {
      const [from, to] = v.range
      const found = await operationApi.conflicts({ channelCode, contentId: v.contentId, startAt: toIso(from), endAt: toIso(to) })
      if (found.length > 0) return { conflicts: found }
      await operationApi.createDraft({
        channelCode,
        contentId: v.contentId,
        episodeId: v.episodeId ?? null,
        contentRating: v.contentRating || null,
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
        <Form<DraftForm> form={form} layout="vertical" initialValues={{ range: [startAt, startAt.add(1, 'hour')] }}>
          <Form.Item name="contentId" label="작품 ID" rules={[{ required: true, message: '작품 ID를 적어 주세요.' }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="episodeId" label="에피소드 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="contentRating" label="등급">
            <Input maxLength={20} />
          </Form.Item>
          <Form.Item name="range" label="방송 시간" rules={[{ required: true, message: '시간을 골라 주세요.' }]}>
            <DatePicker.RangePicker showTime={{ format: 'HH:mm' }} format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Space>
    </Modal>
  )
}
