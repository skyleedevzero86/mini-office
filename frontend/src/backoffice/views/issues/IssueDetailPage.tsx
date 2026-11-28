import { UploadOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Card, Descriptions, Form, Input, InputNumber, List, Select, Space, Tag, Typography, Upload } from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { authApi } from '@/backoffice/api/auth'
import { operationApi } from '@/backoffice/api/operation'
import type { Attachment, IssueComment } from '@/backoffice/api/types'
import { RequestApprovalModal } from '@/backoffice/components/RequestApprovalModal'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import { issueStatusColor, issueStatusLabel, issueTypeLabel, severityColor, severityLabel } from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'

type ExposureTool = 'exposure_hide_content' | 'exposure_restore_content'

const MAX_FILE_BYTES = 10 * 1024 * 1024

function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`
}

export function IssueDetailPage() {
  const id = Number(useParams().id)
  const { message } = App.useApp()
  const { can } = usePermission()
  const queryClient = useQueryClient()
  const canWrite = can('issue:write')
  const [comment, setComment] = useState('')
  const [resolution, setResolution] = useState('')
  const [assignee, setAssignee] = useState<number | null>(null)
  const [exposureTool, setExposureTool] = useState<ExposureTool | null>(null)

  const issue = useQuery({ queryKey: ['issue', id], queryFn: () => operationApi.issue(id), enabled: id > 0 })
  const attachments = useQuery({ queryKey: ['issue', id, 'attachments'], queryFn: () => operationApi.attachments(id), enabled: id > 0 })
  const contentId = issue.data?.contentId ?? null
  const exposure = useQuery({
    queryKey: ['exposure', contentId],
    queryFn: () => operationApi.exposure(contentId!),
    enabled: contentId !== null,
  })
  const users = useQuery({ queryKey: ['auth', 'users'], queryFn: authApi.users, enabled: can('policy:write'), staleTime: 300_000 })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['issue', id] })
    void queryClient.invalidateQueries({ queryKey: ['issues'] })
  }
  const onError = (e: unknown) => message.error(errorMessage(e))

  const addComment = useMutation({
    mutationFn: () => operationApi.comment(id, comment.trim()),
    onSuccess: () => {
      setComment('')
      refresh()
    },
    onError,
  })
  const assign = useMutation({
    mutationFn: (assigneeId: number) => operationApi.assign(id, assigneeId),
    onSuccess: () => {
      message.success('담당자를 배정했습니다.')
      setAssignee(null)
      refresh()
    },
    onError,
  })
  const resolve = useMutation({
    mutationFn: () => operationApi.resolve(id, resolution.trim()),
    onSuccess: () => {
      message.success('이슈를 해결로 바꿨습니다.')
      setResolution('')
      refresh()
    },
    onError,
  })
  const upload = useMutation({
    mutationFn: (file: File) => operationApi.upload(id, file),
    onSuccess: () => {
      message.success('파일을 올렸습니다.')
      void attachments.refetch()
    },
    onError,
  })

  if (issue.error) return <Alert type="error" message={errorMessage(issue.error)} />
  const d = issue.data
  const resolved = d?.status === 'RESOLVED'

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card loading={issue.isLoading} title={d ? `이슈 #${d.id} ${d.title}` : '이슈'} extra={<Link to={boPath('/issues')}>목록</Link>}>
        {d && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Descriptions column={{ xs: 1, md: 3 }} size="small">
              <Descriptions.Item label="유형">{issueTypeLabel[d.type] ?? d.type}</Descriptions.Item>
              <Descriptions.Item label="심각도">
                <StatusTag value={d.severity} labels={severityLabel} colors={severityColor} />
              </Descriptions.Item>
              <Descriptions.Item label="상태">
                <StatusTag value={d.status} labels={issueStatusLabel} colors={issueStatusColor} />
              </Descriptions.Item>
              <Descriptions.Item label="작품">
                {d.contentId ? <Link to={boPath(`/contents/${d.contentId}`)}>#{d.contentId}</Link> : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="등록자">{d.reporter}</Descriptions.Item>
              <Descriptions.Item label="담당자">{d.assigneeId ? `#${d.assigneeId}` : '-'}</Descriptions.Item>
              <Descriptions.Item label="등록">{fmtDateTime(d.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="해결">{fmtDateTime(d.resolvedAt)}</Descriptions.Item>
            </Descriptions>
            <Alert
              type="info"
              message="이슈 본문은 밖에서 들어온 글입니다"
              description={<Typography.Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{d.description}</Typography.Paragraph>}
            />
            {d.resolution && <Alert type="success" message="해결 내용" description={d.resolution} />}
          </Space>
        )}
      </Card>

      {d?.contentId && (
        <Card title="작품 노출">
          <Space wrap>
            {exposure.data ? (
              exposure.data.exposed ? (
                <Tag color="green">노출 중</Tag>
              ) : (
                <Space>
                  <Tag color="red">비노출</Tag>
                  {exposure.data.reason}
                  {exposure.data.issueId && exposure.data.issueId !== d.id && (
                    <Link to={boPath(`/issues/${exposure.data.issueId}`)}>근거 이슈 #{exposure.data.issueId}</Link>
                  )}
                </Space>
              )
            ) : (
              exposure.error && <Typography.Text type="danger">{errorMessage(exposure.error)}</Typography.Text>
            )}
            {can('exposure:write') && exposure.data?.exposed && (
              <Button danger onClick={() => setExposureTool('exposure_hide_content')}>
                숨김 승인 요청
              </Button>
            )}
            {can('exposure:write') && exposure.data && !exposure.data.exposed && (
              <Button onClick={() => setExposureTool('exposure_restore_content')}>숨김 해제 승인 요청</Button>
            )}
          </Space>
        </Card>
      )}

      <Card title={`댓글 ${d?.comments.length ?? 0}`}>
        <List<IssueComment>
          dataSource={d?.comments ?? []}
          locale={{ emptyText: '댓글이 없습니다' }}
          renderItem={(c) => (
            <List.Item>
              <List.Item.Meta
                title={
                  <Space>
                    {c.author}
                    {c.source === 'AGENT' && <Tag color="purple">에이전트</Tag>}
                    <Typography.Text type="secondary">{fmtDateTime(c.createdAt)}</Typography.Text>
                  </Space>
                }
                description={<span style={{ whiteSpace: 'pre-wrap' }}>{c.body}</span>}
              />
            </List.Item>
          )}
        />
        {canWrite && (
          <Space.Compact style={{ width: '100%', marginTop: 12 }}>
            <Input.TextArea value={comment} onChange={(e) => setComment(e.target.value)} autoSize={{ minRows: 2, maxRows: 6 }} maxLength={4000} placeholder="댓글" />
            <Button type="primary" loading={addComment.isPending} disabled={!comment.trim()} onClick={() => addComment.mutate()}>
              남기기
            </Button>
          </Space.Compact>
        )}
      </Card>

      <Card
        title="첨부"
        extra={
          canWrite && (
            <Upload
              showUploadList={false}
              beforeUpload={(file) => {
                if (file.size > MAX_FILE_BYTES) {
                  message.error('파일은 10MB까지 올릴 수 있습니다.')
                  return Upload.LIST_IGNORE
                }
                upload.mutate(file)
                return false
              }}
            >
              <Button icon={<UploadOutlined />} loading={upload.isPending}>
                파일 올리기
              </Button>
            </Upload>
          )
        }
      >
        {attachments.error && <Alert type="error" message={errorMessage(attachments.error)} />}
        <List<Attachment>
          loading={attachments.isLoading}
          dataSource={attachments.data ?? []}
          locale={{ emptyText: '첨부가 없습니다' }}
          renderItem={(a) => (
            <List.Item
              actions={
                a.downloadUrl
                  ? [
                      <a key="down" href={a.downloadUrl} target="_blank" rel="noreferrer">
                        내려받기
                      </a>,
                    ]
                  : []
              }
            >
              <List.Item.Meta
                title={a.fileName}
                description={`${fileSize(a.sizeBytes)} · ${a.uploadedBy} · ${fmtDateTime(a.createdAt)}${a.downloadExpiresAt ? ` · 링크 만료 ${fmtDateTime(a.downloadExpiresAt)}` : ''}`}
              />
            </List.Item>
          )}
        />
      </Card>

      {canWrite && d && !resolved && (
        <Card title="처리">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Space wrap>
              {users.data ? (
                <Select
                  style={{ width: 240 }}
                  placeholder="담당자"
                  value={assignee ?? undefined}
                  onChange={setAssignee}
                  options={users.data.filter((u) => u.status === 'ACTIVE').map((u) => ({ value: u.id, label: `${u.name} (${u.email})` }))}
                />
              ) : (
                <InputNumber min={1} placeholder="담당자 번호" value={assignee} onChange={(v) => setAssignee(v)} style={{ width: 160 }} />
              )}
              <Button disabled={!assignee} loading={assign.isPending} onClick={() => assignee && assign.mutate(assignee)}>
                담당자 배정
              </Button>
            </Space>
            <Form layout="vertical">
              <Form.Item label="해결 내용">
                <Input.TextArea value={resolution} onChange={(e) => setResolution(e.target.value)} rows={3} maxLength={2000} />
              </Form.Item>
              <Button type="primary" disabled={!resolution.trim()} loading={resolve.isPending} onClick={() => resolve.mutate()}>
                해결로 바꾸기
              </Button>
            </Form>
          </Space>
        </Card>
      )}

      {exposureTool && d?.contentId && (
        <RequestApprovalModal
          open
          toolName={exposureTool}
          reasonArg="reason"
          args={exposureTool === 'exposure_hide_content' ? { contentId: d.contentId, issueId: d.id } : { contentId: d.contentId }}
          onClose={() => setExposureTool(null)}
          onRequested={() => void exposure.refetch()}
        />
      )}
    </Space>
  )
}
