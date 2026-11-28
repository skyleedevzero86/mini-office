import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Alert, Card, Segmented, Space, Table, Typography } from 'antd'
import { useNavigate, useSearchParams } from 'react-router'
import { governanceApi } from '@/backoffice/api/governance'
import type { Approval, ApprovalStatus } from '@/backoffice/api/types'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import { actorTypeColor, actorTypeLabel, approvalStatusColor, approvalStatusLabel, label, riskColor, riskLabel, toolLabel } from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'

type View = 'PENDING' | 'ALL' | 'MINE'

export function ApprovalListPage() {
  const navigate = useNavigate()
  const { can } = usePermission()
  const canDecide = can('approval:decide')
  const [params, setParams] = useSearchParams()
  const view = (params.get('view') as View | null) ?? (canDecide ? 'PENDING' : 'MINE')
  const page = Number(params.get('page') ?? 1)
  const size = 20

  const status: ApprovalStatus | undefined = view === 'PENDING' ? 'PENDING' : undefined
  const list = useQuery({
    queryKey: ['approvals', view, page],
    queryFn: () => governanceApi.approvals({ status, mine: view === 'MINE', _start: (page - 1) * size, _end: page * size, _order: 'desc' }),
    placeholderData: keepPreviousData,
  })

  const set = (patch: Record<string, string>) => setParams({ ...Object.fromEntries(params), ...patch })

  return (
    <Card title="승인함">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space wrap>
          <Segmented<View>
            value={view}
            onChange={(v) => set({ view: v, page: '1' })}
            options={[
              ...(canDecide ? [{ value: 'PENDING' as View, label: '승인 대기' }, { value: 'ALL' as View, label: '전체' }] : []),
              { value: 'MINE', label: '내 요청' },
            ]}
          />
          {!canDecide && <Typography.Text type="secondary">승인 권한이 없어 내가 올린 요청만 보입니다.</Typography.Text>}
        </Space>
        {list.error && <Alert type="error" message={errorMessage(list.error)} />}
        <Table<Approval>
          rowKey="id"
          loading={list.isFetching}
          dataSource={list.data?.data ?? []}
          onRow={(r) => ({ onClick: () => navigate(boPath(`/approvals/${r.id}`)), style: { cursor: 'pointer' } })}
          pagination={{ current: page, pageSize: size, total: list.data?.total ?? 0, onChange: (p) => set({ page: String(p) }) }}
          locale={{ emptyText: view === 'PENDING' ? '기다리는 승인 요청이 없습니다' : '승인 요청이 없습니다' }}
          columns={[
            { title: '번호', dataIndex: 'id', width: 80 },
            {
              title: '툴',
              dataIndex: 'toolName',
              render: (v: string) => (
                <Space direction="vertical" size={0}>
                  {label(toolLabel, v)}
                  <Typography.Text type="secondary" code>
                    {v}
                  </Typography.Text>
                </Space>
              ),
            },
            { title: '위험', dataIndex: 'riskLevel', width: 110, render: (v: string) => <StatusTag value={v} labels={riskLabel} colors={riskColor} /> },
            {
              title: '요청자',
              render: (_, r) => (
                <Space>
                  <StatusTag value={r.requesterType} labels={actorTypeLabel} colors={actorTypeColor} />
                  {r.requesterName ?? r.requesterId}
                </Space>
              ),
            },
            { title: '채널', dataIndex: 'scopeChannel', width: 80, render: (v: string | null) => v ?? '-' },
            { title: '상태', dataIndex: 'status', width: 100, render: (v: string) => <StatusTag value={v} labels={approvalStatusLabel} colors={approvalStatusColor} /> },
            { title: '요청', dataIndex: 'createdAt', width: 150, render: (v: string) => fmtDateTime(v) },
            { title: '만료', dataIndex: 'expiresAt', width: 150, render: (v: string) => fmtDateTime(v) },
          ]}
        />
      </Space>
    </Card>
  )
}
