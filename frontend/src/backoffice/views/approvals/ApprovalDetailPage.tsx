import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Descriptions, Input, Modal, Space, Typography } from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { contentApi } from '@/backoffice/api/content'
import { governanceApi } from '@/backoffice/api/governance'
import { operationApi } from '@/backoffice/api/operation'
import type { Approval } from '@/backoffice/api/types'
import { ArgumentsView } from '@/backoffice/components/ArgumentsView'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import {
  actorTypeColor, actorTypeLabel, approvalStatusColor, approvalStatusLabel, label, riskColor, riskLabel, slotStatusLabel, toolLabel,
} from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'

const num = (v: unknown): number | null => (typeof v === 'number' ? v : typeof v === 'string' && /^\d+$/.test(v) ? Number(v) : null)

function useCurrent(approval: Approval | undefined) {
  const { can } = usePermission()
  const tool = approval?.toolName ?? ''
  const args = approval?.arguments ?? {}
  const slotId = tool.startsWith('schedule_') ? num(args.slotId) : null
  const contentId = tool.startsWith('exposure_') ? num(args.contentId) : null
  const titleId = tool === 'content_link_external_id' ? num(args.entityId) : tool.startsWith('content_') ? num(args.titleId) : null
  const pending = approval?.status === 'PENDING'

  const slot = useQuery({ queryKey: ['slot', slotId], queryFn: () => operationApi.slot(slotId!), enabled: pending && slotId !== null && can('schedule:read') })
  const exposure = useQuery({
    queryKey: ['exposure', contentId],
    queryFn: () => operationApi.exposure(contentId!),
    enabled: pending && contentId !== null && can('issue:read'),
  })
  const title = useQuery({
    queryKey: ['content', 'title', titleId],
    queryFn: () => contentApi.detail(titleId!),
    enabled: pending && titleId !== null && can('content:read'),
  })

  const current: Record<string, unknown> = {}
  if (slot.data) {
    const after = tool === 'schedule_publish_slot' ? 'PUBLISHED' : tool === 'schedule_cancel_slot' ? 'CANCELED' : slot.data.status
    current.slotStatus = { before: label(slotStatusLabel, slot.data.status), after: label(slotStatusLabel, after) }
    current.slotTime = { before: `${fmtDateTime(slot.data.startAt)} ~ ${fmtDateTime(slot.data.endAt)}`, after: `${fmtDateTime(slot.data.startAt)} ~ ${fmtDateTime(slot.data.endAt)}` }
  }
  if (exposure.data) {
    const after = tool === 'exposure_hide_content' ? false : tool === 'exposure_restore_content' ? true : exposure.data.exposed
    current.exposed = { before: exposure.data.exposed ? '노출' : '비노출', after: after ? '노출' : '비노출' }
  }
  if (title.data && tool === 'content_link_external_id') {
    current.externalId = title.data.externalIds.find((x) => x.provider === args.provider)?.externalId ?? null
  }
  if (title.data && tool === 'content_update_localization') {
    const loc = title.data.localizations.find((l) => l.langCode === args.langCode)
    current.localTitle = loc?.localTitle ?? null
    current.overview = loc?.overview ?? null
  }
  const error = slot.error ?? exposure.error ?? title.error
  return { current, error, loading: slot.isLoading || exposure.isLoading || title.isLoading, titleName: title.data?.title.originalTitle }
}

export function ApprovalDetailPage() {
  const id = Number(useParams().id)
  const { message } = App.useApp()
  const { can, isMe } = usePermission()
  const queryClient = useQueryClient()
  const [rejecting, setRejecting] = useState(false)
  const [comment, setComment] = useState('')

  const approval = useQuery({ queryKey: ['approval', id], queryFn: () => governanceApi.approval(id), enabled: id > 0 })
  const a = approval.data
  const target = useCurrent(a)

  const done = (text: string) => (next: Approval) => {
    message.success(`${text} 지금 상태: ${label(approvalStatusLabel, next.status)}`)
    queryClient.setQueryData(['approval', id], next)
    void queryClient.invalidateQueries({ queryKey: ['approvals'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard', 'pending'] })
    setRejecting(false)
    setComment('')
  }
  const onError = (e: unknown) => {
    message.error(errorMessage(e))
    void approval.refetch()
  }
  const approve = useMutation({ mutationFn: () => governanceApi.approve(id, comment.trim() || undefined), onSuccess: done('승인했습니다.'), onError })
  const reject = useMutation({ mutationFn: () => governanceApi.reject(id, comment.trim()), onSuccess: done('반려했습니다.'), onError })

  if (approval.error) return <Alert type="error" message={errorMessage(approval.error)} />

  const mine = a ? isMe(a.requesterId) : false
  const canDecide = can('approval:decide') && a?.status === 'PENDING'

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        loading={approval.isLoading}
        title={a ? `승인 요청 #${a.id} · ${label(toolLabel, a.toolName)}` : '승인 요청'}
        extra={<Link to={boPath('/approvals')}>승인함</Link>}
      >
        {a && (
          <Descriptions column={{ xs: 1, md: 2 }} size="small" bordered>
            <Descriptions.Item label="상태">
              <StatusTag value={a.status} labels={approvalStatusLabel} colors={approvalStatusColor} />
            </Descriptions.Item>
            <Descriptions.Item label="위험">
              <StatusTag value={a.riskLevel} labels={riskLabel} colors={riskColor} />
            </Descriptions.Item>
            <Descriptions.Item label="툴">
              <Typography.Text code>{a.toolName}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="채널">{a.scopeChannel ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="요청자">
              <Space>
                <StatusTag value={a.requesterType} labels={actorTypeLabel} colors={actorTypeColor} />
                {a.requesterName ?? a.requesterId}
                {mine && <Typography.Text type="secondary">나</Typography.Text>}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="요청 일시">{fmtDateTime(a.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="만료">{fmtDateTime(a.expiresAt)}</Descriptions.Item>
            <Descriptions.Item label="추적 번호">
              {a.traceId ? (
                can('audit:read') ? <Link to={boPath(`/audit?traceId=${encodeURIComponent(a.traceId)}`)}>{a.traceId}</Link> : a.traceId
              ) : (
                '-'
              )}
            </Descriptions.Item>
            {a.conversationId && <Descriptions.Item label="에이전트 대화">{a.conversationId}</Descriptions.Item>}
            <Descriptions.Item label="이유" span={2}>
              <span style={{ whiteSpace: 'pre-wrap' }}>{a.reason ?? '-'}</span>
            </Descriptions.Item>
            {a.decidedAt && (
              <Descriptions.Item label="결정" span={2}>
                {a.approverId ?? '-'} · {fmtDateTime(a.decidedAt)}
                {a.decisionComment ? ` · ${a.decisionComment}` : ''}
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Card>

      <Card title="바뀔 값" extra={target.titleName && <Typography.Text type="secondary">{target.titleName}</Typography.Text>}>
        {a?.status === 'PENDING' && target.error && <Alert type="warning" style={{ marginBottom: 12 }} message={`지금 값을 불러오지 못했습니다. ${errorMessage(target.error)}`} />}
        {a && <ArgumentsView args={a.arguments} current={target.current} />}
        {a && a.status !== 'PENDING' && <Typography.Text type="secondary">결정이 끝난 요청이라 지금 값과 비교하지 않습니다.</Typography.Text>}
      </Card>

      {a?.result && (
        <Card title="실행 결과">
          <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{JSON.stringify(a.result, null, 2)}</pre>
        </Card>
      )}

      {canDecide && (
        <Card title="결정">
          {mine ? (
            <Alert type="info" message="자기 요청은 직접 승인하거나 반려할 수 없습니다. 다른 승인자에게 맡겨 주세요." />
          ) : (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Text type="secondary">
                승인하면 실행 직전에 요청자 권한과 대상 상태를 다시 확인한 뒤 실행합니다. 확인에 걸리면 실행 실패로 남습니다.
              </Typography.Text>
              <Input.TextArea value={comment} onChange={(e) => setComment(e.target.value)} rows={2} maxLength={1000} placeholder="의견, 반려할 때는 꼭 적습니다" />
              <Space>
                <Button type="primary" loading={approve.isPending} disabled={reject.isPending} onClick={() => approve.mutate()}>
                  승인
                </Button>
                <Button danger disabled={approve.isPending} onClick={() => setRejecting(true)}>
                  반려
                </Button>
              </Space>
            </Space>
          )}
        </Card>
      )}

      <Modal
        open={rejecting}
        title={`승인 요청 #${id} 반려`}
        okText="반려"
        okButtonProps={{ danger: true, disabled: !comment.trim() }}
        confirmLoading={reject.isPending}
        cancelText="닫기"
        onCancel={() => setRejecting(false)}
        onOk={() => reject.mutate()}
      >
        <Input.TextArea value={comment} onChange={(e) => setComment(e.target.value)} rows={3} maxLength={1000} placeholder="반려 이유" />
      </Modal>
    </Space>
  )
}
