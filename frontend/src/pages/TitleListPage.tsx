import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { catalogApi } from '@/api/endpoints'
import type { TitleListItem, TitleSort } from '@/api/types'
import { Badge, Card, Empty, ErrorState, Loading, Pagination, Poster } from '@/components/ui'
import { fmtDate } from '@/lib/format'
import { posterUrl } from '@/lib/format'
import { label, providerLabel, titleStatusLabel, titleTypeLabel } from '@/lib/labels'

const sorts: Array<{ value: TitleSort; label: string }> = [
  { value: 'RELEASE_DESC', label: '공개일 최신순' },
  { value: 'RELEASE_ASC', label: '공개일 오래된순' },
  { value: 'RATING_DESC', label: '평점 높은순' },
  { value: 'TITLE_ASC', label: '제목순' },
  { value: 'UPDATED_DESC', label: '최근 수정순' },
]

function typeLabelOf(item: TitleListItem) {
  return item.animation ? titleTypeLabel[`${item.titleType}_ANIMATION`] : titleTypeLabel[item.titleType]
}

export function TitleListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const query = params.get('query') ?? ''
  const type = params.get('type') ?? ''
  const genre = params.get('genre') ?? ''
  const animation = params.get('animation') ?? ''
  const sort = (params.get('sort') as TitleSort | null) ?? 'RELEASE_DESC'
  const page = Number(params.get('page') ?? 0)
  const size = Number(params.get('size') ?? 20)
  const view = params.get('view') === 'grid' ? 'grid' : 'table'
  const [draft, setDraft] = useState(query)

  const update = (patch: Record<string, string | number | null>, resetPage = true) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, String(v))
    }
    if (resetPage && !('page' in patch)) next.delete('page')
    setParams(next)
  }

  const genres = useQuery({ queryKey: ['genres'], queryFn: catalogApi.genres, staleTime: 300_000 })
  const list = useQuery({
    queryKey: ['titles', { query, type, genre, animation, sort, page, size }],
    queryFn: () => catalogApi.page({
      query: query || undefined, type: type || undefined, genre: genre || undefined,
      animation: animation === '' ? undefined : animation === 'true', sort, page, size,
    }),
    placeholderData: keepPreviousData,
  })

  const onSearch = (e: FormEvent) => {
    e.preventDefault()
    update({ query: draft.trim() })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>작품 목록</h1>
          <p>신규 콘텐츠 메타(작품·시즌·에피소드) 검색 · 한국어 제목이 없으면 원제를 보여 줍니다</p>
        </div>
        <div className="btn-row" role="group" aria-label="보기 방식">
          <button className={`btn sm ${view === 'table' ? 'primary' : ''}`} onClick={() => update({ view: null }, false)}>표</button>
          <button className={`btn sm ${view === 'grid' ? 'primary' : ''}`} onClick={() => update({ view: 'grid' }, false)}>포스터</button>
        </div>
      </div>

      <Card>
        <form className="toolbar" onSubmit={onSearch}>
          <label className="field" style={{ flex: '1 1 240px' }}>
            <span>검색 (원제·현지화 제목)</span>
            <input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="예: 오징어, Game of Thrones" />
          </label>
          <label className="field">
            <span>유형</span>
            <select value={type} onChange={(e) => update({ type: e.target.value })}>
              <option value="">전체</option>
              <option value="MOVIE">영화</option>
              <option value="SERIES">시리즈</option>
            </select>
          </label>
          <label className="field">
            <span>애니메이션</span>
            <select value={animation} onChange={(e) => update({ animation: e.target.value })}>
              <option value="">전체</option>
              <option value="true">애니메이션만</option>
              <option value="false">실사만</option>
            </select>
          </label>
          <label className="field">
            <span>장르</span>
            <select value={genre} onChange={(e) => update({ genre: e.target.value })}>
              <option value="">전체</option>
              {genres.data?.filter((g) => g.titleCount > 0).map((g) => (
                <option key={g.genreCode} value={g.genreCode}>{g.nameKo} ({g.titleCount})</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>정렬</span>
            <select value={sort} onChange={(e) => update({ sort: e.target.value })}>
              {sorts.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
          </label>
          <button className="btn primary" type="submit">검색</button>
          {(query || type || genre || animation) && (
            <button className="btn" type="button" onClick={() => { setDraft(''); setParams(new URLSearchParams(view === 'grid' ? { view } : {})) }}>초기화</button>
          )}
        </form>
      </Card>

      <Card>
        {list.isPending ? <Loading /> : list.isError ? <ErrorState error={list.error} onRetry={() => list.refetch()} /> : list.data.items.length === 0 ? (
          <Empty>조건에 맞는 작품이 없습니다</Empty>
        ) : view === 'grid' ? (
          <div className="poster-grid">
            {list.data.items.map((t) => (
              <button key={t.titleId} className="poster-card" onClick={() => navigate(`/titles/${t.titleId}`)}>
                <Poster url={posterUrl(t.posterPath, 342)} alt={t.displayTitle} />
                <div className="pc-title">{t.displayTitle}</div>
                <div className="pc-sub">{typeLabelOf(t)} · {t.releaseDate?.slice(0, 4) ?? '미상'}{t.voteAverage != null ? ` · ★ ${t.voteAverage}` : ''}</div>
              </button>
            ))}
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>작품</th><th>유형</th><th>공개일</th><th>상태</th><th className="num">평점</th>
                  <th>장르</th><th>외부 ID</th><th className="num">시즌</th>
                </tr>
              </thead>
              <tbody>
                {list.data.items.map((t) => (
                  <tr key={t.titleId} className="clickable" onClick={() => navigate(`/titles/${t.titleId}`)}>
                    <td>
                      <div className="title-cell">
                        <Poster url={posterUrl(t.posterPath, 92)} alt={t.displayTitle} small />
                        <div>
                          <div className="t">{t.displayTitle}</div>
                          {t.originalTitle !== t.displayTitle && <div className="o">{t.originalTitle}</div>}
                        </div>
                      </div>
                    </td>
                    <td>{typeLabelOf(t)}</td>
                    <td>{fmtDate(t.releaseDate)}</td>
                    <td><Badge tone={t.status === 'RETURNING' ? 'info' : 'plain'}>{label(titleStatusLabel, t.status)}</Badge></td>
                    <td className="num">{t.voteAverage ?? '-'}</td>
                    <td className="wrap"><div className="chips">{t.genres.slice(0, 3).map((g) => <span key={g} className="chip">{g}</span>)}</div></td>
                    <td className="wrap">
                      {t.providers.length === 0 ? <Badge tone="warn">없음</Badge> : (
                        <div className="chips">{t.providers.map((p) => <span key={p} className="chip">{providerLabel[p] ?? p}</span>)}</div>
                      )}
                    </td>
                    <td className="num">{t.titleType === 'SERIES' ? t.seasonCount : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {list.data && (
          <Pagination page={list.data.page} totalPages={list.data.totalPages} total={list.data.total} size={list.data.size}
            onPage={(p) => update({ page: p }, false)} onSize={(s) => update({ size: s })} />
        )}
      </Card>
    </>
  )
}
