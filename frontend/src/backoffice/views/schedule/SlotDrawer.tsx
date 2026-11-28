import { useQuery } from '@tanstack/react-query'
import { Button, Descriptions, Drawer, Space, Tag, Typography } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router'
import { contentApi } from '@/backoffice/api/content'
import type { ScheduleSlot } from '@/backoffice/api/types'
import { RequestApprovalModal } from '@/backoffice/components/RequestApprovalModal'
import { usePermission } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import { label, ratingLabel, slotStatusColor, slotStatusLabel, titleTypeLabel } from '@/backoffice/utils/labels'
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
  const canContent = can('content:read')

  const title = useQuery({
    queryKey: ['content', 'title', slot.contentId],
    queryFn: () => contentApi.detail(slot.contentId),
    enabled: canContent,
    retry: false,
    staleTime: 60_000,
  })
  const t = title.data?.title

  return (
    <Drawer open title={`편성 #${slot.id}`} width={420} onClose={onClose}>
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="상태">
          <Tag color={slotStatusColor[slot.status]}>{label(slotStatusLabel, slot.status)}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="채널">{slot.channelCode}</Descriptions.Item>
        <Descriptions.Item label="작품">
          {canContent ? (
            <Space direction="vertical" size={0}>
              <Link to={boPath(`/contents/${slot.contentId}`)}>
                #{slot.contentId} {t?.originalTitle ?? ''}
              </Link>
              {t && <Typography.Text type="secondary">{label(titleTypeLabel, t.titleType)}</Typography.Text>}
              {title.error && <Typography.Text type="danger">작품 정보를 불러오지 못했습니다.</Typography.Text>}
            </Space>
          ) : (
            `#${slot.contentId}`
          )}
        </Descriptions.Item>
        <Descriptions.Item label="회차 번호">{slot.episodeId ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="관람 등급">{slot.contentRating ? label(ratingLabel, slot.contentRating) : '-'}</Descriptions.Item>
        <Descriptions.Item label="시작">{fmtDateTime(slot.startAt)}</Descriptions.Item>
        <Descriptions.Item label="종료">{fmtDateTime(slot.endAt)}</Descriptions.Item>
        <Descriptions.Item label="작성자">{slot.createdBy}</Descriptions.Item>
        <Descriptions.Item label="승인">
          {slot.approvalId ? <Link to={boPath(`/approvals/${slot.approvalId}`)}>승인 요청 #{slot.approvalId}</Link> : '-'}
        </Descriptions.Item>
      </Descriptions>

      {canPublish && slot.status !== 'CANCELED' && (
        <Space direction="vertical" style={{ marginTop: 16 }}>
          <Space>
            {slot.status === 'DRAFT' && (
              <Button type="primary" onClick={() => setTool('schedule_publish_slot')}>
                게시 승인 요청
              </Button>
            )}
            <Button danger onClick={() => setTool('schedule_cancel_slot')}>
              취소 승인 요청
            </Button>
          </Space>
          <Typography.Text type="secondary">게시와 취소는 승인자가 승인한 뒤에 실행됩니다. 본인 요청은 본인이 승인할 수 없습니다.</Typography.Text>
        </Space>
      )}

      {tool && <RequestApprovalModal open toolName={tool} args={{ slotId: slot.id, channelCode: slot.channelCode }} onClose={() => setTool(null)} />}
    </Drawer>
  )
}
