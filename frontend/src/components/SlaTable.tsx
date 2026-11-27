import type { SlaFreshnessRow } from '@/api/types'
import { fmtDateTime, fmtMinutes } from '@/lib/format'
import { jobLabel } from '@/lib/labels'
import { Badge, Empty } from './ui'

export function SlaTable({ rows }: { rows: SlaFreshnessRow[] }) {
  if (rows.length === 0) return <Empty>사용 중인 배치가 없습니다</Empty>
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>작업</th><th>신선도</th><th>마지막 성공</th><th className="num">경과</th>
            <th className="num">허용 간격</th><th className="num">24시간 실패</th><th className="num">24시간 SLA 위반</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.jobName}>
              <td><b>{jobLabel[r.jobName] ?? r.jobName}</b> <span className="muted mono">{r.jobName}</span></td>
              <td>{r.freshness === 'OK' ? <Badge tone="good">정상</Badge> : <Badge tone="bad">지연(STALE)</Badge>}</td>
              <td>{fmtDateTime(r.lastSuccessAt)}</td>
              <td className="num">{fmtMinutes(r.minutesSinceSuccess)}</td>
              <td className="num">{fmtMinutes(r.maxGapMinutes)}</td>
              <td className="num">{r.failures24h > 0 ? <Badge tone="bad">{r.failures24h}</Badge> : 0}</td>
              <td className="num">{r.slaBreaches24h > 0 ? <Badge tone="warn">{r.slaBreaches24h}</Badge> : 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
