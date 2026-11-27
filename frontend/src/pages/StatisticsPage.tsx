import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '@/api/endpoints'
import { HBarCard, VBarCard } from '@/components/charts'
import { ErrorState, Kpi, Loading } from '@/components/ui'
import { fmtPercent } from '@/lib/format'
import { issueTypeLabel, providerLabel, titleStatusLabel, titleTypeLabel } from '@/lib/labels'

export function StatisticsPage() {
  const stats = useQuery({ queryKey: ['dashboard', 'statistics'], queryFn: dashboardApi.statistics })
  const overview = useQuery({ queryKey: ['dashboard', 'overview'], queryFn: dashboardApi.overview })

  if (stats.isPending) return <Loading />
  if (stats.isError) return <ErrorState error={stats.error} onRetry={() => stats.refetch()} />
  const s = stats.data
  const c = overview.data?.counts
  const thisYear = String(new Date().getFullYear())

  return (
    <>
      <div className="page-head">
        <div>
          <h1>통계</h1>
          <p>신규 콘텐츠 메타 분포와 외부 표준 ID·현지화 커버리지</p>
        </div>
      </div>

      {c && (
        <div className="grid grid-4">
          <Kpi label="외부 ID 연결률" value={fmtPercent(c.titlesWithExternalId, c.totalTitles)} ratio={c.totalTitles ? c.titlesWithExternalId / c.totalTitles : 0} />
          <Kpi label="한국어 현지화율" value={fmtPercent(c.titlesWithKo, c.totalTitles)} ratio={c.totalTitles ? c.titlesWithKo / c.totalTitles : 0} />
          <Kpi label="외부 ID로 확인된 인물" value={fmtPercent(c.personCount - c.nameOnlyPersonCount, c.personCount)} ratio={c.personCount ? 1 - c.nameOnlyPersonCount / c.personCount : 0} />
          <Kpi label="시리즈당 평균 에피소드" value={c.seriesCount ? (c.episodeCount / c.seriesCount).toFixed(1) : '-'} />
        </div>
      )}

      <div className="grid grid-2">
        <HBarCard title="유형별" data={s.byType} labelOf={(l) => titleTypeLabel[l] ?? l} />
        <HBarCard title="상태별" sub="DELETED = 원본에서 지워진 작품 (지우지 않고 상태만 바꾼다)" data={s.byStatus} labelOf={(l) => titleStatusLabel[l] ?? l} />
      </div>
      <VBarCard title="공개 연도별" sub="올해 공개작은 주황으로 표시" data={s.byReleaseYear} highlight={(l) => l === thisYear} />
      <div className="grid grid-2">
        <HBarCard title="장르별 (상위 20)" data={s.byGenre} tall />
        <div className="grid">
          <HBarCard title="외부 ID 제공처별" data={s.byProvider} labelOf={(l) => providerLabel[l] ?? l} />
          <HBarCard title="현지화 언어별" data={s.byLanguage} />
        </div>
      </div>
      <HBarCard title="최근 이관 작업의 검증 문제" labelWidth={190} sub="ERROR는 적재에서 빠지고, INFO는 적재하되 기록만 남긴다"
        data={s.issuesByType} labelOf={(l) => { const [sev, type = ''] = l.split(' '); return `${sev} · ${issueTypeLabel[type] ?? type}` }} />
    </>
  )
}
