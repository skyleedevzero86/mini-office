import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { BatchTrendRow } from '@/api/types'
import { jobLabel } from '@/lib/labels'
import { CHART, ChartCard } from './charts'

export function BatchTrendChart({ rows, days }: { rows: BatchTrendRow[]; days: number }) {
  const byDate = new Map<string, { date: string; 성공: number; 실패: number; SLA위반: number }>()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    byDate.set(key, { date: key.slice(5), 성공: 0, 실패: 0, SLA위반: 0 })
  }
  for (const r of rows) {
    const slot = byDate.get(r.runDate) ?? { date: r.runDate.slice(5), 성공: 0, 실패: 0, SLA위반: 0 }
    slot.성공 += r.successCount
    slot.실패 += r.failedCount
    slot.SLA위반 += r.slaBreachedCount
    byDate.set(r.runDate, slot)
  }
  const data = [...byDate.values()]

  return (
    <ChartCard
      title="배치 실행 추이"
      sub={`최근 ${days}일 · 변경분 동기화·대조·품질 점검 합계`}
      rows={rows.map((r) => ({ date: r.runDate, job: jobLabel[r.jobName] ?? r.jobName, ok: r.successCount, fail: r.failedCount, sla: r.slaBreachedCount, avg: r.avgDurationMs == null ? '-' : `${r.avgDurationMs}ms` }))}
      columns={[
        { key: 'date', label: '날짜' }, { key: 'job', label: '작업' }, { key: 'ok', label: '성공', num: true },
        { key: 'fail', label: '실패', num: true }, { key: 'sla', label: 'SLA 위반', num: true }, { key: 'avg', label: '평균 소요', num: true },
      ]}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid vertical={false} stroke={CHART.grid} />
          <XAxis dataKey="date" tick={{ fill: CHART.axis, fontSize: 12 }} axisLine={false} tickLine={false} />
          <YAxis allowDecimals={false} tick={{ fill: CHART.axis, fontSize: 12 }} axisLine={false} tickLine={false} />
          <Tooltip cursor={{ fill: '#f0efec' }} />
          <Legend iconType="square" wrapperStyle={{ fontSize: 12 }} />
          <Bar dataKey="성공" stackId="a" fill={CHART.good} maxBarSize={28} stroke="#ffffff" strokeWidth={2} />
          <Bar dataKey="실패" stackId="a" fill={CHART.critical} maxBarSize={28} radius={[4, 4, 0, 0]} stroke="#ffffff" strokeWidth={2} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
