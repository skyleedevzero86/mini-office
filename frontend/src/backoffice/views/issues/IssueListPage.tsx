import { PlusOutlined } from '@ant-design/icons'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Input, Select, Space, Table, Typography } from 'antd'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { operationApi } from '@/backoffice/api/operation'
import type { Issue, IssueStatus, IssueType, Severity } from '@/backoffice/api/types'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import { issueStatusColor, issueStatusLabel, issueTypeLabel, severityColor, severityLabel } from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'
import { IssueCreateModal } from './IssueCreateModal'

const options = (map: Record<string, string>) => Object.entries(map).map(([value, label]) => ({ value, label }))

export function IssueListPage() {
  const navigate = useNavigate()
  const { can } = usePermission()
  const [params, setParams] = useSearchParams()
  const [creating, setCreating] = useState(false)
  const type = (params.get('type') ?? undefined) as IssueType | undefined
  const severity = (params.get('severity') ?? undefined) as Severity | undefined
  const status = (params.get('status') ?? undefined) as IssueStatus | undefined
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? 1)
  const size = Number(params.get('size') ?? 20)

  const update = (patch: Record<string, string | number | null | undefined>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === undefined || v === '') next.delete(k)
      else next.set(k, String(v))
    }
    if (!('page' in patch)) next.delete('page')
    setParams(next)
  }

  const list = useQuery({
    queryKey: ['issues', { type, severity, status, q, page, size }],
    queryFn: () =>
      operationApi.issues({ type, severity, status, q: q || undefined, _start: (page - 1) * size, _end: page * size, _sort: 'createdAt', _order: 'desc' }),
    placeholderData: keepPreviousData,
  })

  return (
    <Card
      title="이슈"
      extra={
        can('issue:write') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
            이슈 등록
          </Button>
        )
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search allowClear defaultValue={q} placeholder="제목·내용 검색" style={{ width: 240 }} onSearch={(v) => update({ q: v.trim() })} />
        <Select allowClear placeholder="유형" style={{ width: 130 }} value={type} onChange={(v) => update({ type: v })} options={options(issueTypeLabel)} />
        <Select allowClear placeholder="심각도" style={{ width: 120 }} value={severity} onChange={(v) => update({ severity: v })} options={options(severityLabel)} />
        <Select allowClear placeholder="상태" style={{ width: 120 }} value={status} onChange={(v) => update({ status: v })} options={options(issueStatusLabel)} />
      </Space>
      {list.error && <Alert type="error" message={errorMessage(list.error)} style={{ marginBottom: 16 }} />}
      <Table<Issue>
        rowKey="id"
        loading={list.isFetching}
        dataSource={list.data?.data ?? []}
        onRow={(r) => ({ onClick: () => navigate(boPath(`/issues/${r.id}`)), style: { cursor: 'pointer' } })}
        pagination={{
          current: page,
          pageSize: size,
          total: list.data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `전체 ${t.toLocaleString()}건`,
          onChange: (p, s) => update({ page: p, size: s }),
        }}
        columns={[
          { title: '번호', dataIndex: 'id', width: 80 },
          { title: '심각도', dataIndex: 'severity', width: 90, render: (v: string) => <StatusTag value={v} labels={severityLabel} colors={severityColor} /> },
          { title: '유형', dataIndex: 'type', width: 100, render: (v: string) => issueTypeLabel[v] ?? v },
          {
            title: '제목',
            dataIndex: 'title',
            render: (v: string, r) => (
              <Space direction="vertical" size={0}>
                <Typography.Text strong>{v}</Typography.Text>
                {r.contentId && <Typography.Text type="secondary">작품 #{r.contentId}</Typography.Text>}
              </Space>
            ),
          },
          { title: '상태', dataIndex: 'status', width: 90, render: (v: string) => <StatusTag value={v} labels={issueStatusLabel} colors={issueStatusColor} /> },
          { title: '담당자', dataIndex: 'assigneeId', width: 90, render: (v: number | null) => (v ? `#${v}` : '-') },
          { title: '등록', dataIndex: 'createdAt', width: 150, render: (v: string) => fmtDateTime(v) },
        ]}
      />
      {creating && (
        <IssueCreateModal
          onClose={() => setCreating(false)}
          onCreated={(issue) => navigate(boPath(`/issues/${issue.id}`))}
        />
      )}
    </Card>
  )
}
