import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { migrationApi } from '@/api/endpoints'
import type { VerifyResult } from '@/api/types'
import { useToast } from '@/components/Toast'
import { Badge, Card, Empty, ErrorState, Kpi, Loading, Pagination } from '@/components/ui'
import { fmtDateTime, fmtDuration, fmtNumber } from '@/lib/format'
import { issueTypeLabel, modeLabel, runStatusLabel, sourceTableLabel } from '@/lib/labels'
import { runTone } from './MigrationPage'

const ISSUE_PAGE = 20

export function MigrationRunPage() {
  const runId = Number(useParams().runId)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [severity, setSeverity] = useState('')
  const [page, setPage] = useState(0)
  const [maxChunks, setMaxChunks] = useState(20)
  const [verifyResult, setVerifyResult] = useState<VerifyResult | null>(null)

  const run = useQuery({ queryKey: ['migration-run', runId], queryFn: () => migrationApi.run(runId) })
  const summary = useQuery({ queryKey: ['migration-run', runId, 'summary'], queryFn: () => migrationApi.issueSummary(runId) })
  const issues = useQuery({ queryKey: ['migration-run', runId, 'issues', severity], queryFn: () => migrationApi.issues(runId, severity || undefined, 1000) })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['migration-run', runId] })
    queryClient.invalidateQueries({ queryKey: ['migration-runs'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
  const precheck = useMutation({
    mutationFn: () => migrationApi.precheck(runId),
    onSuccess: (r) => { toast.ok(`사전 검증: ERROR ${r.errorCount}건, INFO ${r.infoCount}건`, 'ERROR 행은 적재에서 빠집니다. 데이터는 바꾸지 않았습니다.'); invalidate() },
    onError: (e) => { toast.error(e); invalidate() },
  })
  const backfill = useMutation({
    mutationFn: () => migrationApi.backfill(runId, maxChunks),
    onSuccess: (r) => {
      toast.ok(r.done ? `백필 완료: 묶음 ${r.chunks}개, 작품 ${r.titles}편 (${r.elapsedMs}ms)` : `묶음 ${r.chunks}개 반영 — 다시 누르면 체크포인트에서 이어 갑니다`)
      invalidate()
    },
    onError: (e) => { toast.error(e); invalidate() },
  })
  const sync = useMutation({
    mutationFn: () => migrationApi.sync(runId, run.data?.batchSize ?? 500),
    onSuccess: (r) => { toast.ok(`동기화: 변경 ${r.changes}건 (작품 ${r.titles}, 삭제 ${r.deleted}, 오류 ${r.errors})`); invalidate() },
    onError: (e) => { toast.error(e); invalidate() },
  })
  const verify = useMutation({
    mutationFn: () => migrationApi.verify(runId),
    onSuccess: (r) => { setVerifyResult(r); toast.ok(r.clean ? '대조 결과 0/0/0 — 읽기 전환이 가능합니다' : `대조: 누락 ${r.missing} · 불일치 ${r.mismatch} · 고아 ${r.orphan}`); invalidate() },
    onError: (e) => { toast.error(e); invalidate() },
  })

  if (run.isPending) return <Loading />
  if (run.isError) return <ErrorState error={run.error} onRetry={() => run.refetch()} />
  const r = run.data
  const busy = precheck.isPending || backfill.isPending || sync.isPending || verify.isPending
  const list = issues.data ?? []

  return (
    <>
      <div className="between"><Link to="/migration">← 이관 작업 기록</Link></div>
      <div className="page-head">
        <div>
          <h1>작업 #{r.runId} · {modeLabel[r.mode]} <Badge tone={runTone[r.status]}>{runStatusLabel[r.status]}</Badge></h1>
          <p>{r.description} · 생성 {fmtDateTime(r.createdAt)} · 시작 {fmtDateTime(r.startedAt)} · 종료 {fmtDateTime(r.finishedAt)} ({fmtDuration(r.startedAt, r.finishedAt)})</p>
        </div>
        <div className="btn-row">
          {r.mode === 'BACKFILL' && (
            <>
              <button className="btn" disabled={busy} onClick={() => precheck.mutate()}>사전 검증</button>
              <label className="hstack secondary">묶음
                <input type="number" min={1} max={1000} value={maxChunks} onChange={(e) => setMaxChunks(Number(e.target.value))} style={{ width: 80 }} />개까지
              </label>
              <button className="btn primary" disabled={busy} onClick={() => backfill.mutate()}>{backfill.isPending ? '백필 중…' : '백필 실행'}</button>
            </>
          )}
          {r.mode === 'SYNC' && <button className="btn primary" disabled={busy} onClick={() => sync.mutate()}>변경분 동기화</button>}
          {r.mode === 'VERIFY' && <button className="btn primary" disabled={busy} onClick={() => verify.mutate()}>대조 실행</button>}
        </div>
      </div>

      {r.errorMessage && <ErrorState error={new Error(r.errorMessage)} />}

      <div className="grid grid-4">
        <Kpi label="반영한 묶음 × 크기" value={`${r.batchCount} × ${r.batchSize}`} foot={`시도 ${r.attemptCount}회 (실패 뒤 다시 실행하면 이어 감)`} />
        <Kpi label="체크포인트 (영화 / TV / 애니)" value={`${r.lastMovieSeq} / ${r.lastTvSeq} / ${r.lastCartoonSeq}`} foot="원본 순번 어디까지 반영했는지" />
        <Kpi label="적재 (작품 · 출연 · 회차)" value={`${fmtNumber(r.upsertedTitles)} · ${fmtNumber(r.upsertedCredits)} · ${fmtNumber(r.upsertedEpisodes)}`} />
        <Kpi label="검증 문제 (ERROR · INFO)" value={`${fmtNumber(r.errorCount)} · ${fmtNumber(r.infoCount)}`} foot="ERROR는 적재 제외, INFO는 적재 + 기록" />
      </div>

      {verifyResult && (
        <Card title="대조 결과" sub="원본 테이블별 건수와 내용 지문(CRC32) 비교">
          <table>
            <thead><tr><th>원본 테이블</th><th className="num">원본</th><th className="num">신규</th><th className="num">누락</th><th className="num">불일치</th><th className="num">고아</th></tr></thead>
            <tbody>{verifyResult.tables.map((t) => (
              <tr key={t.sourceTable}>
                <td>{sourceTableLabel[t.sourceTable] ?? t.sourceTable}</td><td className="num">{t.sourceCount}</td><td className="num">{t.targetCount}</td>
                <td className="num">{t.missingCount}</td><td className="num">{t.mismatchCount}</td><td className="num">{t.orphanCount}</td>
              </tr>
            ))}</tbody>
          </table>
        </Card>
      )}

      <div className="grid grid-2">
        <Card title="문제 유형별 건수">
          {summary.isPending ? <Loading /> : (summary.data ?? []).length === 0 ? <Empty>검증 문제가 없습니다</Empty> : (
            <table>
              <thead><tr><th>원본</th><th>유형</th><th>심각도</th><th className="num">건수</th></tr></thead>
              <tbody>{summary.data!.map((s) => (
                <tr key={`${s.sourceTable}-${s.issueType}-${s.severity}`}>
                  <td>{sourceTableLabel[s.sourceTable] ?? s.sourceTable}</td>
                  <td>{issueTypeLabel[s.issueType] ?? s.issueType}</td>
                  <td>{s.severity === 'ERROR' ? <Badge tone="bad">ERROR</Badge> : <Badge tone="info">INFO</Badge>}</td>
                  <td className="num">{s.cnt}</td>
                </tr>
              ))}</tbody>
            </table>
          )}
        </Card>
        <Card title="검증 문제 목록" actions={
          <select value={severity} onChange={(e) => { setSeverity(e.target.value); setPage(0) }} aria-label="심각도">
            <option value="">전체</option><option value="ERROR">ERROR</option><option value="INFO">INFO</option>
          </select>
        }>
          {issues.isPending ? <Loading /> : list.length === 0 ? <Empty>없음</Empty> : (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>원본 키</th><th>유형</th><th>내용</th></tr></thead>
                  <tbody>{list.slice(page * ISSUE_PAGE, (page + 1) * ISSUE_PAGE).map((i) => (
                    <tr key={i.issueId}>
                      <td className="mono">{i.sourceTable}:{i.sourceKey}</td>
                      <td>{i.severity === 'ERROR' ? <Badge tone="bad">{issueTypeLabel[i.issueType] ?? i.issueType}</Badge> : <Badge tone="info">{issueTypeLabel[i.issueType] ?? i.issueType}</Badge>}</td>
                      <td className="wrap secondary">{i.message}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
              <Pagination page={page} totalPages={Math.ceil(list.length / ISSUE_PAGE)} total={list.length} size={ISSUE_PAGE} onPage={setPage} />
            </>
          )}
        </Card>
      </div>
    </>
  )
}
