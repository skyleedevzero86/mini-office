import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { dashboardApi, operationApi } from '@/api/endpoints'
import type { BatchJob, BatchOutcome } from '@/api/types'
import { BatchTrendChart } from '@/components/BatchTrendChart'
import { SlaTable } from '@/components/SlaTable'
import { useToast } from '@/components/Toast'
import { Badge, Card, Empty, ErrorState, Loading, Pagination } from '@/components/ui'
import { fmtDateTime, fmtDuration, fmtNumber } from '@/lib/format'
import { jobLabel, sourceTableLabel } from '@/lib/labels'

const RUNS_PAGE = 15

function outcomeMessage(o: BatchOutcome): [string, string | null] {
  if (o.skipped) return [`${jobLabel[o.job]}: 건너뜀`, o.message]
  if (!o.success) return [`${jobLabel[o.job]}: 실패`, o.message]
  return [`${jobLabel[o.job]}: 완료 (처리 ${o.processed}건)`, o.slaBreached ? 'SLA 허용 시간을 넘겼습니다' : null]
}

export function MonitoringPage() {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [job, setJob] = useState<BatchJob | ''>('')
  const [page, setPage] = useState(0)
  const [days, setDays] = useState(7)

  const sla = useQuery({ queryKey: ['sla'], queryFn: operationApi.sla, refetchInterval: 30_000 })
  const runs = useQuery({ queryKey: ['batch-runs', job], queryFn: () => operationApi.batchRuns(job || undefined, 200), refetchInterval: 30_000 })
  const trend = useQuery({ queryKey: ['dashboard', 'trend', days], queryFn: () => dashboardApi.batchTrend(days) })
  const quality = useQuery({ queryKey: ['quality-latest'], queryFn: dashboardApi.latestQuality })
  const verify = useQuery({ queryKey: ['verify-latest'], queryFn: dashboardApi.latestVerify })

  const refresh = () => queryClient.invalidateQueries()
  const run = useMutation({
    mutationFn: async (which: BatchJob): Promise<BatchOutcome> => {
      if (which === 'LEGACY_SYNC') return operationApi.runSync()
      if (which === 'LEGACY_VERIFY') return operationApi.runVerify()
      return (await operationApi.runQuality()).outcome
    },
    onSuccess: (o) => {
      const [title, detail] = outcomeMessage(o)
      if (o.success) toast.ok(title, detail)
      else toast.error(new Error(`${title}${detail ? ` — ${detail}` : ''}`))
      refresh()
    },
    onError: toast.error,
  })

  if (sla.isError) return <ErrorState error={sla.error} onRetry={() => sla.refetch()} />
  const allRuns = runs.data ?? []
  const pageRuns = allRuns.slice(page * RUNS_PAGE, (page + 1) * RUNS_PAGE)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>모니터링</h1>
          <p>정기 배치(동기화 1분·대조 매시간·품질 매일) 실행 기록, SLA, 데이터 품질 · 30초마다 갱신</p>
        </div>
        <div className="btn-row">
          {(['LEGACY_SYNC', 'LEGACY_VERIFY', 'QUALITY_CHECK'] as const).map((j) => (
            <button key={j} className="btn" disabled={run.isPending} onClick={() => run.mutate(j)}>{jobLabel[j]} 지금 실행</button>
          ))}
        </div>
      </div>

      <Card title="SLA 신선도">{sla.isPending ? <Loading /> : <SlaTable rows={sla.data} />}</Card>

      <div className="between">
        <span />
        <label className="field" style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <span>기간</span>
          <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {[7, 14, 30].map((d) => <option key={d} value={d}>최근 {d}일</option>)}
          </select>
        </label>
      </div>
      {trend.data ? <BatchTrendChart rows={trend.data} days={days} /> : <Card title="배치 실행 추이"><Loading /></Card>}

      <Card title="배치 실행 기록" sub="같은 작업이 실행 중이면 겹쳐 돌지 않고, 허용 시간의 3배를 넘긴 RUNNING은 멈춘 것으로 정리합니다"
        actions={
          <select value={job} onChange={(e) => { setJob(e.target.value as BatchJob | ''); setPage(0) }} aria-label="작업 필터">
            <option value="">전체 작업</option>
            {(['LEGACY_SYNC', 'LEGACY_VERIFY', 'QUALITY_CHECK'] as const).map((j) => <option key={j} value={j}>{jobLabel[j]}</option>)}
          </select>
        }>
        {runs.isPending ? <Loading /> : allRuns.length === 0 ? <Empty>실행 기록이 없습니다. 위의 "지금 실행"으로 한 번 돌려 보세요</Empty> : (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th className="num">#</th><th>작업</th><th>상태</th><th>시작</th><th className="num">소요</th><th className="num">처리</th><th>SLA</th><th>실행 서버</th><th>사유</th></tr></thead>
                <tbody>{pageRuns.map((r) => (
                  <tr key={r.jobRunId}>
                    <td className="num mono">{r.jobRunId}</td>
                    <td>{jobLabel[r.jobName] ?? r.jobName}</td>
                    <td>{r.status === 'SUCCESS' ? <Badge tone="good">성공</Badge> : r.status === 'FAILED' ? <Badge tone="bad">실패</Badge> : <Badge tone="info">실행 중</Badge>}</td>
                    <td>{fmtDateTime(r.startedAt)}</td>
                    <td className="num">{fmtDuration(r.startedAt, r.finishedAt)}</td>
                    <td className="num">{fmtNumber(r.processedCount)}</td>
                    <td>{r.slaBreached ? <Badge tone="warn">위반</Badge> : <span className="muted">-</span>}</td>
                    <td className="mono muted">{r.worker}</td>
                    <td className="wrap secondary">{r.errorMessage ?? ''}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={Math.ceil(allRuns.length / RUNS_PAGE)} total={allRuns.length} size={RUNS_PAGE} onPage={setPage} />
          </>
        )}
      </Card>

      <div className="grid grid-2">
        <Card title="최근 데이터 품질 점검" sub={quality.data ? `점검 #${quality.data.jobRunId} · 위반이 허용 건수를 넘으면 FAIL` : undefined}>
          {quality.isPending ? <Loading /> : !quality.data ? <Empty>점검 기록이 없습니다</Empty> : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>규칙</th><th>심각도</th><th className="num">위반</th><th className="num">허용</th><th>판정</th><th>예시</th></tr></thead>
                <tbody>{quality.data.rules.map((r) => (
                  <tr key={r.ruleCode}>
                    <td><span className="mono muted">{r.ruleCode}</span> {r.ruleName}</td>
                    <td>{r.severity === 'HIGH' ? <Badge tone="warn">HIGH</Badge> : <Badge>LOW</Badge>}</td>
                    <td className="num">{fmtNumber(r.violationCount)}</td>
                    <td className="num">{fmtNumber(r.threshold)}</td>
                    <td>{r.result === 'PASS' ? <Badge tone="good">PASS</Badge> : <Badge tone="bad">FAIL</Badge>}</td>
                    <td className="mono muted ellipsis" title={r.sampleKeys ?? ''}>{r.sampleKeys ?? ''}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </Card>
        <Card title="최근 원본·신규 대조" sub={verify.data ? `run ${verify.data.latest.runId} · ${fmtDateTime(verify.data.latest.finishedAt)} · 내용 지문(CRC32) 비교` : undefined}>
          {verify.isPending ? <Loading /> : !verify.data ? <Empty>대조 기록이 없습니다</Empty> : (
            <table>
              <thead><tr><th>원본 테이블</th><th className="num">원본</th><th className="num">신규</th><th className="num">누락</th><th className="num">불일치</th><th className="num">고아</th></tr></thead>
              <tbody>{verify.data.tables.map((t) => (
                <tr key={t.sourceTable}>
                  <td>{sourceTableLabel[t.sourceTable] ?? t.sourceTable}</td>
                  <td className="num">{fmtNumber(t.sourceCount)}</td>
                  <td className="num">{fmtNumber(t.targetCount)}</td>
                  {[t.missingCount, t.mismatchCount, t.orphanCount].map((n, i) => (
                    <td key={i} className="num">{n === 0 ? '0' : <Badge tone="bad">{n}</Badge>}</td>
                  ))}
                </tr>
              ))}</tbody>
            </table>
          )}
        </Card>
      </div>
    </>
  )
}
