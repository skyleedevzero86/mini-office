import { useState, type ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { LabelCount } from '@/api/types'
import { fmtNumber } from '@/lib/format'
import { Card, Empty } from './ui'

// 차트 색 (dataviz 기본 팔레트: 단일 계열 = 1번 파랑, 상태 = 성공 초록 / 실패 빨강)
export const CHART = {
  series1: '#2a78d6',
  series2: '#eb6834',
  good: '#0ca30c',
  critical: '#d03b3b',
  warning: '#fab219',
  grid: '#ecebe7',
  axis: '#7a7974',
}

const axisTick = { fill: CHART.axis, fontSize: 12 }

/** 차트 + 표 보기 전환 (색만으로 읽지 않아도 되게 숫자 표를 같이 둔다) */
export function ChartCard({ title, sub, rows, columns, children, tall, autoHeight }: {
  title: string
  sub?: ReactNode
  rows: Array<Record<string, ReactNode>>
  columns: Array<{ key: string; label: string; num?: boolean }>
  children: ReactNode
  tall?: boolean
  autoHeight?: boolean
}) {
  const [asTable, setAsTable] = useState(false)
  return (
    <Card title={title} sub={sub} actions={
      <button className="btn sm" onClick={() => setAsTable((v) => !v)} aria-pressed={asTable}>{asTable ? '차트 보기' : '표 보기'}</button>
    }>
      {rows.length === 0 ? <Empty>데이터가 없습니다</Empty> : asTable ? (
        <div className="table-wrap">
          <table>
            <thead><tr>{columns.map((c) => <th key={c.key} className={c.num ? 'num' : ''}>{c.label}</th>)}</tr></thead>
            <tbody>{rows.map((r, i) => <tr key={i}>{columns.map((c) => <td key={c.key} className={c.num ? 'num' : ''}>{r[c.key]}</td>)}</tr>)}</tbody>
          </table>
        </div>
      ) : <div className={autoHeight ? '' : `chart-box ${tall ? 'tall' : ''}`}>{children}</div>}
    </Card>
  )
}

/** 이름별 건수 가로 막대 (한 계열이라 범례 없이 제목이 계열 이름이다) */
export function HBarCard({ title, sub, data, labelOf = (l) => l, tall, labelWidth = 120 }: {
  title: string
  sub?: ReactNode
  data: LabelCount[]
  labelOf?: (label: string) => string
  tall?: boolean
  labelWidth?: number
}) {
  const rows = data.map((d) => ({ name: labelOf(d.label), cnt: d.cnt }))
  const height = Math.max(160, rows.length * 30 + 30)
  return (
    <ChartCard title={title} sub={sub} tall={tall} autoHeight
      rows={rows.map((r) => ({ name: r.name, cnt: fmtNumber(r.cnt) }))}
      columns={[{ key: 'name', label: '항목' }, { key: 'cnt', label: '건수', num: true }]}>
      <ResponsiveContainer width="100%" height={Math.min(height, tall ? 600 : 260)}>
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 24, bottom: 4, left: 8 }} barCategoryGap={6}>
          <CartesianGrid horizontal={false} stroke={CHART.grid} />
          <XAxis type="number" allowDecimals={false} tick={axisTick} axisLine={false} tickLine={false} />
          <YAxis type="category" dataKey="name" width={labelWidth} interval={0} tick={axisTick} axisLine={false} tickLine={false} />
          <Tooltip cursor={{ fill: '#f0efec' }} formatter={(v) => [fmtNumber(Number(v)), '건수']} />
          <Bar dataKey="cnt" fill={CHART.series1} radius={[0, 4, 4, 0]} maxBarSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

/** 연도처럼 순서가 있는 항목의 세로 막대 */
export function VBarCard({ title, sub, data, highlight }: { title: string; sub?: ReactNode; data: LabelCount[]; highlight?: (label: string) => boolean }) {
  const rows = data.map((d) => ({ name: d.label, cnt: d.cnt }))
  return (
    <ChartCard title={title} sub={sub}
      rows={rows.map((r) => ({ name: r.name, cnt: fmtNumber(r.cnt) }))}
      columns={[{ key: 'name', label: '항목' }, { key: 'cnt', label: '건수', num: true }]}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 4, left: -16 }}>
          <CartesianGrid vertical={false} stroke={CHART.grid} />
          <XAxis dataKey="name" tick={axisTick} axisLine={false} tickLine={false} interval="preserveStartEnd" />
          <YAxis allowDecimals={false} tick={axisTick} axisLine={false} tickLine={false} />
          <Tooltip cursor={{ fill: '#f0efec' }} formatter={(v) => [fmtNumber(Number(v)), '건수']} />
          <Bar dataKey="cnt" radius={[4, 4, 0, 0]} maxBarSize={28}>
            {rows.map((r) => <Cell key={r.name} fill={highlight?.(r.name) ? CHART.series2 : CHART.series1} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
