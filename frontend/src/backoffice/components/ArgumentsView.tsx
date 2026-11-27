import { Table, Typography } from 'antd'
import { argLabel, label } from '@/backoffice/utils/labels'

interface Row {
  key: string
  before?: unknown
  after: unknown
  hasBefore: boolean
  changed: boolean
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v)
}

function show(v: unknown): string {
  if (v === undefined) return ''
  if (v === null) return '없음'
  if (typeof v === 'string') return v
  return JSON.stringify(v)
}

export function toRows(args: Record<string, unknown>, current?: Record<string, unknown>): Row[] {
  const rows: Row[] = []
  const seen = new Set<string>()
  for (const [k, v] of Object.entries(args)) {
    seen.add(k)
    if (isRecord(v) && 'before' in v && 'after' in v) {
      rows.push({ key: k, before: v.before, after: v.after, hasBefore: true, changed: show(v.before) !== show(v.after) })
    } else if (current && k in current) {
      rows.push({ key: k, before: current[k], after: v, hasBefore: true, changed: show(current[k]) !== show(v) })
    } else {
      rows.push({ key: k, after: v, hasBefore: false, changed: false })
    }
  }
  for (const [k, v] of Object.entries(current ?? {})) {
    if (seen.has(k) || !isRecord(v) || !('before' in v && 'after' in v)) continue
    rows.push({ key: k, before: v.before, after: v.after, hasBefore: true, changed: show(v.before) !== show(v.after) })
  }
  return rows
}

interface Props {
  readonly args: Record<string, unknown>
  readonly current?: Record<string, unknown>
}

export function ArgumentsView({ args, current }: Props) {
  const rows = toRows(args, current)
  const hasDiff = rows.some((r) => r.hasBefore)
  if (rows.length === 0) return <Typography.Text type="secondary">인자 없음</Typography.Text>
  return (
    <Table<Row>
      size="small"
      rowKey="key"
      pagination={false}
      dataSource={rows}
      columns={[
        { title: '항목', dataIndex: 'key', width: 150, render: (k: string) => <span title={k}>{label(argLabel, k)}</span> },
        ...(hasDiff
          ? [
              {
                title: '지금',
                key: 'before',
                render: (_: unknown, r: Row) => (r.hasBefore ? <Typography.Text type="secondary">{show(r.before)}</Typography.Text> : null),
              },
            ]
          : []),
        {
          title: hasDiff ? '바뀔 값' : '값',
          key: 'after',
          render: (_: unknown, r: Row) => (r.changed ? <Typography.Text strong mark>{show(r.after)}</Typography.Text> : show(r.after)),
        },
      ]}
    />
  )
}
