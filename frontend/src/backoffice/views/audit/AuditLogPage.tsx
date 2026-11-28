import { DownloadOutlined } from '@ant-design/icons'
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { Alert, App, Button, Card, DatePicker, Input, Select, Space, Table, Timeline, Typography } from 'antd'
import type { Dayjs } from 'dayjs'
import { useSearchParams } from 'react-router'
import { governanceApi } from '@/backoffice/api/governance'
import type { ActorType, AuditFilter, AuditLog, AuditResult } from '@/backoffice/api/types'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage } from '@/backoffice/platform'
import { actorTypeColor, actorTypeLabel, auditResultColor, auditResultLabel } from '@/backoffice/utils/labels'
import { fmtDateTime, seoul, toIso } from '@/backoffice/utils/time'

const options = (map: Record<string, string>) => Object.entries(map).map(([value, label]) => ({ value, label }))
const show = (v: unknown) => (v === null || v === undefined ? '-' : typeof v === 'string' ? v : JSON.stringify(v, null, 2))

function Values({ log }: { readonly log: AuditLog }) {
  return (
    <Space align="start" wrap style={{ width: '100%' }}>
      <div style={{ minWidth: 280 }}>
        <Typography.Text type="secondary">바뀌기 전</Typography.Text>
        <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{show(log.beforeValue)}</pre>
      </div>
      <div style={{ minWidth: 280 }}>
        <Typography.Text type="secondary">바뀐 뒤</Typography.Text>
        <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{show(log.afterValue)}</pre>
      </div>
      {log.reason && (
        <div>
          <Typography.Text type="secondary">사유</Typography.Text>
          <div>{log.reason}</div>
        </div>
      )}
    </Space>
  )
}

export function AuditLogPage() {
  const { message } = App.useApp()
  const [params, setParams] = useSearchParams()
  const get = (k: string) => params.get(k) ?? undefined
  const filter: AuditFilter = {
    actorType: get('actorType') as ActorType | undefined,
    actorId: get('actorId'),
    action: get('action'),
    resourceType: get('resourceType'),
    resourceId: get('resourceId'),
    result: get('result') as AuditResult | undefined,
    traceId: get('traceId'),
    from: get('from'),
    to: get('to'),
  }
  const tracing = !!filter.traceId
  const page = Number(params.get('page') ?? 1)
  const size = tracing ? 100 : 20

  const update = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (!v) next.delete(k)
      else next.set(k, v)
    }
    if (!('page' in patch)) next.delete('page')
    setParams(next)
  }

  const logs = useQuery({
    queryKey: ['audit', filter, page],
    queryFn: () => governanceApi.auditLogs(filter, { _start: (page - 1) * size, _end: page * size, _order: tracing ? 'asc' : 'desc' }),
    placeholderData: keepPreviousData,
  })

  const exporting = useMutation({
    mutationFn: () => governanceApi.exportAudit(filter),
    onSuccess: (r) => {
      message.success(`${r.rows.toLocaleString()}건을 내보냈습니다${r.truncated ? '. 최대 건수를 넘어 일부만 담았습니다' : ''}.`)
      window.open(r.downloadUrl, '_blank', 'noopener')
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  const range: [Dayjs, Dayjs] | null = filter.from && filter.to ? [seoul(filter.from), seoul(filter.to)] : null

  return (
    <Card
      title="감사 로그"
      extra={
        <Button icon={<DownloadOutlined />} loading={exporting.isPending} onClick={() => exporting.mutate()}>
          CSV 내보내기
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select allowClear placeholder="행위자" style={{ width: 120 }} value={filter.actorType} onChange={(v) => update({ actorType: v })} options={options(actorTypeLabel)} />
        <Select allowClear placeholder="결과" style={{ width: 120 }} value={filter.result} onChange={(v) => update({ result: v })} options={options(auditResultLabel)} />
        <Input.Search allowClear placeholder="행위자 번호" defaultValue={filter.actorId} style={{ width: 140 }} onSearch={(v) => update({ actorId: v.trim() })} />
        <Input.Search allowClear placeholder="작업 이름" defaultValue={filter.action} style={{ width: 200 }} onSearch={(v) => update({ action: v.trim() })} />
        <Input.Search allowClear placeholder="대상 유형" defaultValue={filter.resourceType} style={{ width: 140 }} onSearch={(v) => update({ resourceType: v.trim() })} />
        <Input.Search allowClear placeholder="추적 번호" defaultValue={filter.traceId} style={{ width: 280 }} onSearch={(v) => update({ traceId: v.trim() })} />
        <DatePicker.RangePicker
          showTime={{ format: 'HH:mm' }}
          format="YYYY-MM-DD HH:mm"
          value={range}
          onChange={(v) => update({ from: v?.[0] ? toIso(v[0]) : undefined, to: v?.[1] ? toIso(v[1]) : undefined })}
        />
      </Space>

      {logs.error && <Alert type="error" message={errorMessage(logs.error)} style={{ marginBottom: 16 }} />}

      {tracing ? (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <Typography.Text>
              추적 번호 <Typography.Text code>{filter.traceId}</Typography.Text> 한 흐름 {logs.data?.total ?? 0}건
            </Typography.Text>
            <Button size="small" onClick={() => update({ traceId: undefined })}>
              흐름 보기 끄기
            </Button>
          </Space>
          <Timeline
            items={(logs.data?.data ?? []).map((log) => ({
              color: auditResultColor[log.result] === 'red' ? 'red' : auditResultColor[log.result] === 'gold' ? 'gray' : 'green',
              children: (
                <Space direction="vertical" size={2} style={{ width: '100%' }}>
                  <Space wrap>
                    <Typography.Text type="secondary">{fmtDateTime(log.createdAt)}</Typography.Text>
                    <Typography.Text>{log.service}</Typography.Text>
                    <code>{log.action}</code>
                    <StatusTag value={log.actorType} labels={actorTypeLabel} colors={actorTypeColor} />
                    {log.actorId}
                    {log.onBehalfOf && <Typography.Text type="secondary">대신 실행 {log.onBehalfOf}</Typography.Text>}
                    <StatusTag value={log.result} labels={auditResultLabel} colors={auditResultColor} />
                    {log.approvalId && <Typography.Text type="secondary">승인 #{log.approvalId}</Typography.Text>}
                  </Space>
                  <Values log={log} />
                </Space>
              ),
            }))}
          />
        </Space>
      ) : (
        <Table<AuditLog>
          rowKey="id"
          size="small"
          loading={logs.isFetching}
          dataSource={logs.data?.data ?? []}
          expandable={{ expandedRowRender: (log) => <Values log={log} /> }}
          pagination={{ current: page, pageSize: size, total: logs.data?.total ?? 0, onChange: (p) => update({ page: String(p) }) }}
          columns={[
            { title: '일시', dataIndex: 'createdAt', width: 150, render: (v: string) => fmtDateTime(v) },
            { title: '서비스', dataIndex: 'service', width: 100 },
            { title: '작업', dataIndex: 'action', render: (v: string) => <code>{v}</code> },
            {
              title: '행위자',
              width: 180,
              render: (_, log) => (
                <Space>
                  <StatusTag value={log.actorType} labels={actorTypeLabel} colors={actorTypeColor} />
                  {log.actorId}
                </Space>
              ),
            },
            { title: '대상', width: 160, render: (_, log) => (log.resourceType ? `${log.resourceType} ${log.resourceId ?? ''}` : '-') },
            { title: '결과', dataIndex: 'result', width: 90, render: (v: string) => <StatusTag value={v} labels={auditResultLabel} colors={auditResultColor} /> },
            {
              title: '흐름',
              dataIndex: 'traceId',
              width: 90,
              render: (v: string | null) =>
                v ? (
                  <Button size="small" type="link" onClick={() => update({ traceId: v })}>
                    흐름 보기
                  </Button>
                ) : (
                  '-'
                ),
            },
          ]}
        />
      )}
    </Card>
  )
}
