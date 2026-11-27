import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { dashboardApi, migrationApi } from '@/api/endpoints'
import type { MigrationMode, RunStatus } from '@/api/types'
import { useToast } from '@/components/Toast'
import { Badge, Card, Empty, ErrorState, Loading, Notice, Pagination, type Tone } from '@/components/ui'
import { fmtDateTime, fmtDuration, fmtMinutes, fmtNumber } from '@/lib/format'
import { modeLabel, runStatusLabel } from '@/lib/labels'

const PAGE = 15

export const runTone: Record<RunStatus, Tone> = {
  CREATED: 'plain', RUNNING: 'info', COMPLETED: 'good', COMPLETED_WITH_ISSUES: 'warn', FAILED: 'bad',
}

export function MigrationPage() {
  const toast = useToast()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<MigrationMode>('BACKFILL')
  const [batchSize, setBatchSize] = useState(500)
  const [description, setDescription] = useState('')
  const [page, setPage] = useState(0)

  const runs = useQuery({ queryKey: ['migration-runs'], queryFn: () => migrationApi.runs(200) })
  const overview = useQuery({ queryKey: ['dashboard', 'overview'], queryFn: dashboardApi.overview })

  const create = useMutation({
    mutationFn: () => migrationApi.createRun(mode, batchSize, description || undefined),
    onSuccess: (run) => {
      toast.ok(`이관 작업 #${run.runId} (${modeLabel[run.mode]})을 만들었습니다`)
      queryClient.invalidateQueries({ queryKey: ['migration-runs'] })
      navigate(`/migration/${run.runId}`)
    },
    onError: toast.error,
  })
  const switchSource = useMutation({
    mutationFn: migrationApi.switchReadSource,
    onSuccess: (r) => { toast.ok(`읽기 원천을 ${r.readSource}로 바꿨습니다`); queryClient.invalidateQueries() },
    onError: toast.error,
  })

  if (runs.isError) return <ErrorState error={runs.error} onRetry={() => runs.refetch()} />
  const o = overview.data
  const all = runs.data ?? []

  return (
    <>
      <div className="page-head">
        <div>
          <h1>이관</h1>
          <p>사전 검증 → 백필(묶음·체크포인트) → 변경분 동기화(트리거 기록) → 대조(건수·지문) → 읽기 전환</p>
        </div>
      </div>

      {o && (
        <Card title="읽기 원천" sub="NEW 전환은 최근 대조가 2시간 안이고 누락·불일치·고아가 모두 0일 때만 됩니다. LEGACY 되돌리기는 언제든 됩니다">
          <div className="between" style={{ flexWrap: 'wrap' }}>
            <div className="hstack">
              <Badge tone={o.readSource === 'NEW' ? 'good' : 'info'}>현재 {o.readSource}</Badge>
              {o.latestVerify ? (
                <span className="secondary">최근 대조 run {o.latestVerify.runId}: 누락 {o.latestVerify.missing} · 불일치 {o.latestVerify.mismatch} · 고아 {o.latestVerify.orphan} ({fmtMinutes(o.latestVerify.ageMinutes)} 전)</span>
              ) : <span className="secondary">대조 기록 없음</span>}
            </div>
            <div className="btn-row">
              <button className="btn primary" disabled={o.readSource === 'NEW' || !o.readSwitchAllowed || switchSource.isPending} onClick={() => switchSource.mutate('NEW')}>NEW로 전환</button>
              <button className="btn" disabled={o.readSource === 'LEGACY' || switchSource.isPending} onClick={() => switchSource.mutate('LEGACY')}>LEGACY로 되돌리기</button>
            </div>
          </div>
          {o.readSource === 'LEGACY' && !o.readSwitchAllowed && (
            <div style={{ marginTop: 12 }}>
              <Notice tone="info" title="전환 전에 할 일">
                {o.counts.totalTitles === 0 ? 'BACKFILL 작업을 만들어 사전 검증과 백필을 끝까지 돌리세요. ' : ''}
                {o.counts.pendingChanges > 0 ? `동기화 대기 ${o.counts.pendingChanges}건을 반영하세요 (SYNC 작업 또는 모니터링의 동기화). ` : ''}
                VERIFY 작업으로 대조해 0/0/0을 확인하세요.
              </Notice>
            </div>
          )}
        </Card>
      )}

      <Card title="새 이관 작업">
        <form className="toolbar" onSubmit={(e) => { e.preventDefault(); create.mutate() }}>
          <label className="field">
            <span>모드</span>
            <select value={mode} onChange={(e) => setMode(e.target.value as MigrationMode)}>
              <option value="BACKFILL">BACKFILL · 기존 데이터 묶음 적재</option>
              <option value="SYNC">SYNC · 레거시 변경분 반영</option>
              <option value="VERIFY">VERIFY · 원본·신규 대조</option>
            </select>
          </label>
          <label className="field" style={{ width: 130 }}>
            <span>묶음 크기 (1~5000)</span>
            <input type="number" min={1} max={5000} value={batchSize} onChange={(e) => setBatchSize(Number(e.target.value))} />
          </label>
          <label className="field" style={{ flex: '1 1 220px' }}>
            <span>설명</span>
            <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="예: 1차 백필" />
          </label>
          <button className="btn primary" type="submit" disabled={create.isPending}>작업 만들기</button>
        </form>
      </Card>

      <Card title="이관 작업 기록">
        {runs.isPending ? <Loading /> : all.length === 0 ? <Empty>작업이 없습니다</Empty> : (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr>
                  <th className="num">#</th><th>모드</th><th>상태</th><th>설명</th><th className="num">묶음</th><th className="num">시도</th>
                  <th className="num">ERROR</th><th className="num">INFO</th><th className="num">작품</th><th>시작</th><th className="num">소요</th>
                </tr></thead>
                <tbody>{all.slice(page * PAGE, (page + 1) * PAGE).map((r) => (
                  <tr key={r.runId} className="clickable" onClick={() => navigate(`/migration/${r.runId}`)}>
                    <td className="num mono">{r.runId}</td>
                    <td>{modeLabel[r.mode]}</td>
                    <td><Badge tone={runTone[r.status]}>{runStatusLabel[r.status]}</Badge></td>
                    <td className="wrap">{r.description}</td>
                    <td className="num">{r.batchCount} × {r.batchSize}</td>
                    <td className="num">{r.attemptCount}</td>
                    <td className="num">{r.errorCount ? <Badge tone="bad">{r.errorCount}</Badge> : 0}</td>
                    <td className="num">{fmtNumber(r.infoCount)}</td>
                    <td className="num">{fmtNumber(r.upsertedTitles)}</td>
                    <td>{fmtDateTime(r.startedAt ?? r.createdAt)}</td>
                    <td className="num">{fmtDuration(r.startedAt, r.finishedAt)}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={Math.ceil(all.length / PAGE)} total={all.length} size={PAGE} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  )
}
