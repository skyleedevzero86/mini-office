import { Button, Descriptions, Drawer, Space, Tag } from 'antd'
import { useState } from 'react'
import type { ScheduleSlot } from '@/backoffice/api/types'
import { RequestApprovalModal } from '@/backoffice/components/RequestApprovalModal'
import { usePermission } from '@/backoffice/platform'
import { label, slotStatusColor, slotStatusLabel } from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'

interface Props {
  readonly slot: ScheduleSlot
  readonly onClose: () => void
}

type SlotTool = 'schedule_publish_slot' | 'schedule_cancel_slot'

export function SlotDrawer({ slot, onClose }: Props) {
  const { can } = usePermission()
  const [tool, setTool] = useState<SlotTool | null>(null)
  const canPublish = can('schedule:publish')

  return (
    <Drawer open title={`편성 #${slot.id}`} width={420} onClose={onClose}>
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="상태">
          <Tag color={slotStatusColor[slot.status]}>{label(slotStatusLabel, slot.status)}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="채널">{slot.channelCode}</Descriptions.Item>
        <Descriptions.Item label="작품 ID">{slot.contentId}</Descriptions.Item>
        <Descriptions.Item label="에피소드 ID">{slot.episodeId ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="등급">{slot.contentRating ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="시작">{fmtDateTime(slot.startAt)}</Descriptions.Item>
        <Descriptions.Item label="종료">{fmtDateTime(slot.endAt)}</Descriptions.Item>
        <Descriptions.Item label="작성자">{slot.createdBy}</Descriptions.Item>
        <Descriptions.Item label="승인">{slot.approvalId ? `#${slot.approvalId}` : '-'}</Descriptions.Item>
      </Descriptions>

      {canPublish && slot.status !== 'CANCELED' && (
        <Space style={{ marginTop: 16 }}>
          {slot.status === 'DRAFT' && (
            <Button type="primary" onClick={() => setTool('schedule_publish_slot')}>
              게시 승인 요청
            </Button>
          )}
          <Button danger onClick={() => setTool('schedule_cancel_slot')}>
            취소 승인 요청
          </Button>
        </Space>
      )}

      {tool && <RequestApprovalModal open toolName={tool} args={{ slotId: slot.id, channelCode: slot.channelCode }} onClose={() => setTool(null)} />}
    </Drawer>
  )
}
