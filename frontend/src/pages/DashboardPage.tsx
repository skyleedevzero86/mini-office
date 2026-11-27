import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { dashboardApi } from '@/api/endpoints'
import type { DashboardOverview } from '@/api/types'
import { BatchTrendChart } from '@/components/BatchTrendChart'
import { HBarCard } from '@/components/charts'
import { SlaTable } from '@/components/SlaTable'
import { Badge, Card, ErrorState, Kpi, Loading } from '@/components/ui'
import { fmtDateTime, fmtMinutes, fmtNumber, fmtPercent } from '@/lib/format'
import { providerLabel, titleTypeLabel } from '@/lib/labels'

const TREND_DAYS = 7

function MigrationSteps({ o }: { o: DashboardOverview }) {
  const c = o.counts
  const backfilled = c.totalTitles > 0
  const synced = backfilled && c.pendingChanges === 0
  const verified = o.latestVerify != null && o.latestVerify.missing + o.latestVerify.mismatch + o.latestVerify.orphan === 0
  const switched = o.readSource === 'NEW'
  const steps = [
    { l: '사전 검증', s: '오류·안내 건수 기록', done: backfilled },
    { l: '백필', s: backfilled ? `작품 ${fmtNumber(c.totalTitles)}편 적재` : '묶음 단위 적재', done: backfilled },
    { l: '변경분 동기화', s: c.pendingChanges > 0 ? `대기 ${fmtNumber(c.pendingChanges)}건` : '대기 0건', done: synced },
    { l: '원본·신규 대조', s: o.latestVerify ? `${o.latestVerify.missing}/${o.latestVerify.mismatch}/${o.latestVerify.orphan} · ${fmtMinutes(o.latestVerify.ageMinutes)} 전` : '기록 없음', done: verified },
    { l: '읽기 전환', s: switched ? 'NEW로 읽는 중' : o.readSwitchAllowed ? '전환 가능' : '대조 0/0/0 필요', done: switched },
  ]
  const now = steps.findIndex((s) => !s.done)
  return (
    <div className="steps">
      {steps.map((s, i) => (
        <div key={s.l} className={`step ${s.done ? 'done' : i === now ? 'now' : ''}`}>
          <div className="n">STEP {i + 1}{s.done ? ' ✓' : ''}</div>
          <div className="l">{s.l}</div>
          <div className="s">{s.s}</div>
        </div>
      ))}
    </div>
  )
}

export function DashboardPage() {
  const overview = useQuery({ queryKey: ['dashboard', 'overview'], queryFn: dashboardApi.overview, refetchInterval: 30_000 })
  const stats = useQuery({ queryKey: ['dashboard', 'statistics'], queryFn: dashboardApi.statistics })
  const trend = useQuery({ queryKey: ['dashboard', 'trend', TREND_DAYS], queryFn: () => dashboardApi.batchTrend(TREND_DAYS), refetchInterval: 60_000 })

  if (overview.isPending) return <Loading />
  if (overview.isError) return <ErrorState error={overview.error} onRetry={() => overview.refetch()} />
  const o = overview.data
  const c = o.counts

  return (
    <>
      <div className="page-head">
        <div>
          <h1>운영 대시보드</h1>
          <p>레거시(movies·MergedTVShow·cartoon_video) → 콘텐츠 메타 무중단 이관과 메타 품질 현황 · 30초마다 갱신</p>
        </div>
        <div className="btn-row">
          <Link className="btn" to="/monitoring">모니터링</Link>
          <Link className="btn primary" to="/migration">이관 진행</Link>
        </div>
      </div>

      <Card title="이관 단계" sub={<>읽기 원천 <b>{o.readSource}</b> · 최근 대조 {o.latestVerify ? `run ${o.latestVerify.runId} (${fmtDateTime(o.latestVerify.finishedAt)})` : '없음'}</>}
        actions={o.readSwitchAllowed && o.readSource === 'LEGACY' ? <Badge tone="good">NEW 전환 가능</Badge> : undefined}>
        <MigrationSteps o={o} />
      </Card>

      <div className="grid grid-4">
        <Kpi label="작품 (삭제 제외)" value={c.totalTitles} foot={`영화 ${fmtNumber(c.movieCount)} · 시리즈 ${fmtNumber(c.seriesCount)} · 애니 ${fmtNumber(c.animationCount)}`} />
        <Kpi label="시즌 · 에피소드" value={`${fmtNumber(c.seasonCount)} · ${fmtNumber(c.episodeCount)}`} foot={`원본에서 삭제된 작품 ${fmtNumber(c.deletedCount)}편`} />
        <Kpi label="외부 ID 연결률 (TMDB·IMDB·KOBIS)" value={fmtPercent(c.titlesWithExternalId, c.totalTitles)}
          ratio={c.totalTitles ? c.titlesWithExternalId / c.totalTitles : 0} foot={`${fmtNumber(c.titlesWithExternalId)} / ${fmtNumber(c.totalTitles)}편`} />
        <Kpi label="한국어 현지화율" value={fmtPercent(c.titlesWithKo, c.totalTitles)}
          ratio={c.totalTitles ? c.titlesWithKo / c.totalTitles : 0} foot={`${fmtNumber(c.titlesWithKo)}편에 ko 제목`} />
        <Kpi label="인물 (이름만 있는 인물)" value={c.personCount} foot={`동명이인 확인 필요 ${fmtNumber(c.nameOnlyPersonCount)}명`}
          ratio={c.personCount ? 1 - c.nameOnlyPersonCount / c.personCount : 0} />
        <Kpi label="레거시 원본 행" value={c.legacyRows} foot="세 원본 테이블 합계" />
        <Kpi label="동기화 대기 변경" value={c.pendingChanges}
          foot={c.oldestPendingChangeAt ? `가장 오래된 변경 ${fmtDateTime(c.oldestPendingChangeAt)}` : '모두 반영됨'} />
        <Kpi label="데이터 품질" value={o.latestQuality ? (o.latestQuality.failedRules === 0 ? '전부 통과' : `FAIL ${o.latestQuality.failedRules}`) : '점검 전'}
          foot={o.latestQuality ? `규칙 ${o.latestQuality.totalRules}개 · 점검 #${o.latestQuality.jobRunId}` : '모니터링에서 품질 점검 실행'} />
      </div>

      <Card title="배치 SLA 신선도" sub="마지막 성공이 허용 간격을 넘으면 STALE">
        <SlaTable rows={o.sla} />
      </Card>

      {trend.data ? <BatchTrendChart rows={trend.data} days={TREND_DAYS} /> : <Card title="배치 실행 추이"><Loading /></Card>}

      {stats.data && (
        <div className="grid grid-2">
          <HBarCard title="유형별 작품 수" data={stats.data.byType} labelOf={(l) => titleTypeLabel[l] ?? l} />
          <HBarCard title="외부 ID 제공처별 연결 수" sub="연결이 없는 제공처도 0으로 표시" data={stats.data.byProvider} labelOf={(l) => providerLabel[l] ?? l} />
        </div>
      )}
    </>
  )
}
