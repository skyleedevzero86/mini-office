import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { catalogApi, tmdbApi } from '@/api/endpoints'
import type { Provider, TitleDetail, TmdbTitle } from '@/api/types'
import { useToast } from '@/components/Toast'
import { Badge, Card, Empty, ErrorState, Loading, Notice, Poster } from '@/components/ui'
import { fmtDate, fmtNumber, posterUrl } from '@/lib/format'
import { label, providerLabel, providerLink, roleLabel, titleStatusLabel, titleTypeLabel } from '@/lib/labels'

function Hero({ detail, tmdb, genreName }: { detail: TitleDetail; tmdb: TmdbTitle | undefined; genreName: (code: string) => string }) {
  const t = detail.title
  const ko = detail.localizations.find((l) => l.langCode === 'ko')
  const linked = tmdb?.configured && tmdb.linked
  const name = (linked ? tmdb.title : null) ?? ko?.localTitle ?? t.originalTitle
  const overview = (linked && tmdb.overview) || ko?.overview
  const poster = (linked ? tmdb.posterUrl : null) ?? posterUrl(t.posterPath, 342)
  const type = t.animation ? titleTypeLabel[`${t.titleType}_ANIMATION`] : titleTypeLabel[t.titleType]

  return (
    <div className="backdrop" style={linked && tmdb.backdropUrl ? { backgroundImage: `url(${tmdb.backdropUrl})` } : undefined}>
      <div className="backdrop-inner">
        <Poster url={poster} alt={name} />
        <div>
          <div className="chips" style={{ marginBottom: 8 }}>
            <span className="chip">{type}</span>
            <span className="chip">{label(titleStatusLabel, t.status)}</span>
            {detail.genres.map((g) => <span key={g} className="chip">{genreName(g)}</span>)}
          </div>
          <h1>{name}</h1>
          {t.originalTitle !== name && <div className="o">{t.originalTitle}</div>}
          {linked && tmdb.tagline && <div className="tagline">“{tmdb.tagline}”</div>}
          {overview ? <p className="overview">{overview}</p> : <p className="overview" style={{ opacity: 0.7 }}>줄거리가 없습니다.</p>}
          <div className="facts">
            <span>공개 {fmtDate(t.releaseDate)}</span>
            {(t.runtimeMin ?? (linked ? tmdb.runtime : null)) && <span>{t.runtimeMin ?? tmdb?.runtime}분{t.titleType === 'SERIES' ? ' (회당)' : ''}</span>}
            {t.voteAverage != null && <span>평점(원본) ★ {t.voteAverage}</span>}
            {linked && tmdb.voteAverage != null && <span>TMDB ★ {tmdb.voteAverage.toFixed(1)} ({fmtNumber(tmdb.voteCount)}명)</span>}
            {linked && tmdb.seasons != null && <span>시즌 {tmdb.seasons} · {fmtNumber(tmdb.episodes)}화</span>}
            {linked && tmdb.networks.length > 0 && <span>{tmdb.networks.join(', ')}</span>}
            {linked && tmdb.directors.length > 0 && <span>{t.titleType === 'SERIES' ? '크리에이터' : '감독'} {tmdb.directors.join(', ')}</span>}
            {linked && tmdb.tmdbUrl && <a href={tmdb.tmdbUrl} target="_blank" rel="noreferrer">TMDB에서 보기 ↗</a>}
            {linked && tmdb.imdbId && <a href={`https://www.imdb.com/title/${tmdb.imdbId}/`} target="_blank" rel="noreferrer">IMDb ↗</a>}
          </div>
        </div>
      </div>
    </div>
  )
}

function TmdbPanel({ detail, tmdb, loading, error }: { detail: TitleDetail; tmdb: TmdbTitle | undefined; loading: boolean; error: unknown }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const t = detail.title
  const [query, setQuery] = useState(detail.localizations.find((l) => l.langCode === 'ko')?.localTitle ?? t.originalTitle)
  const [year, setYear] = useState(t.releaseDate?.slice(0, 4) ?? '')
  const type = t.titleType === 'SERIES' ? 'SERIES' : 'MOVIE'

  const search = useMutation({
    mutationFn: () => tmdbApi.search(type, query, year ? Number(year) : undefined),
    onError: toast.error,
  })
  const link = useMutation({
    mutationFn: (args: { provider: Provider; tmdbId: number }) =>
      catalogApi.saveExternalId({ operation: 'C', entityType: 'TITLE', entityId: t.titleId, provider: args.provider, externalId: String(args.tmdbId) }),
    onSuccess: () => {
      toast.ok('TMDB ID를 연결했습니다', '작업 로그(work_log)에 남았습니다. 이제 TMDB 정보를 반영할 수 있습니다.')
      queryClient.invalidateQueries({ queryKey: ['title', t.titleId] })
      queryClient.invalidateQueries({ queryKey: ['title-tmdb', t.titleId] })
    },
    onError: toast.error,
  })
  const apply = useMutation({
    mutationFn: () => tmdbApi.apply(t.titleId),
    onSuccess: (result) => {
      toast.ok('TMDB 정보를 반영했습니다', result.note)
      queryClient.invalidateQueries({ queryKey: ['title', t.titleId] })
      queryClient.invalidateQueries({ queryKey: ['titles'] })
    },
    onError: toast.error,
  })

  if (loading) return <Card title="TMDB"><Loading /></Card>
  if (error) return <Card title="TMDB"><ErrorState error={error} /></Card>
  if (!tmdb) return null
  if (!tmdb.configured) {
    return (
      <Card title="TMDB">
        <Notice tone="info" title="TMDB 키가 설정되지 않았습니다">
          백엔드를 TMDB_API_KEY 환경변수(v3 API 키 또는 v4 읽기 토큰)와 함께 띄우면 포스터·줄거리·출연진을 TMDB에서 가져오고, 연결 후보를 검색할 수 있습니다.
        </Notice>
      </Card>
    )
  }
  if (tmdb.linked) {
    return (
      <Card title="TMDB" sub={`${providerLabel[tmdb.provider ?? ''] ?? tmdb.provider} ${tmdb.tmdbId} · 실시간 조회(30분 캐시)`}
        actions={<button className="btn primary sm" disabled={apply.isPending} onClick={() => apply.mutate()}>
          {apply.isPending ? '반영 중…' : 'TMDB 정보 반영'}
        </button>}>
        <p className="secondary" style={{ marginTop: 0 }}>
          반영하면 포스터·IMDB ID·영어 제목/줄거리를 신규 메타에 저장합니다 (sp_title_tmdb_apply). 원제·공개일·상영시간·상태는 레거시가 원천이라 바꾸지 않습니다.
        </p>
        {tmdb.cast.length === 0 ? <Empty>TMDB 출연진 정보가 없습니다</Empty> : (
          <div className="cast-grid">
            {tmdb.cast.map((p) => (
              <div key={p.tmdbPersonId} className="cast">
                <div className="face" style={p.profileUrl ? { backgroundImage: `url(${p.profileUrl})` } : undefined}>
                  {!p.profileUrl && p.name.slice(0, 1)}
                </div>
                <div className="n">{p.name}</div>
                {p.character && <div className="c">{p.character}</div>}
              </div>
            ))}
          </div>
        )}
      </Card>
    )
  }
  return (
    <Card title="TMDB 연결 후보" sub="TMDB ID가 없는 작품입니다. 후보를 찾아 운영자가 확인하고 연결합니다 (자동으로 붙이지 않습니다)">
      <form className="toolbar" onSubmit={(e: FormEvent) => { e.preventDefault(); search.mutate() }}>
        <label className="field" style={{ flex: '1 1 220px' }}>
          <span>검색어</span>
          <input value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <label className="field" style={{ width: 110 }}>
          <span>연도</span>
          <input value={year} onChange={(e) => setYear(e.target.value.replace(/\D/g, '').slice(0, 4))} placeholder="선택" />
        </label>
        <button className="btn primary" type="submit" disabled={search.isPending || !query.trim()}>{search.isPending ? '찾는 중…' : 'TMDB 검색'}</button>
      </form>
      {search.data && (
        <div className="candidates" style={{ marginTop: 14 }}>
          {search.data.length === 0 && <Empty>후보가 없습니다. 검색어(원제)나 연도를 바꿔 보세요</Empty>}
          {search.data.map((c) => (
            <div key={c.tmdbId} className="candidate">
              <Poster url={c.posterUrl} alt={c.title ?? ''} small />
              <div>
                <b>{c.title}</b> <span className="muted">{c.originalTitle !== c.title ? c.originalTitle : ''} · {c.date || '날짜 없음'} · TMDB {c.tmdbId}</span>
                {c.overview && <p>{c.overview}</p>}
              </div>
              {c.linkedTitleId ? (
                c.linkedTitleId === t.titleId ? <Badge tone="good">연결됨</Badge> : <Link to={`/titles/${c.linkedTitleId}`}><Badge tone="warn">다른 작품에 연결됨</Badge></Link>
              ) : (
                <button className="btn sm" disabled={link.isPending} onClick={() => link.mutate({ provider: c.provider, tmdbId: c.tmdbId })}>이 작품으로 연결</button>
              )}
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}

function ExternalIdCard({ detail }: { detail: TitleDetail }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [provider, setProvider] = useState<Provider>('IMDB')
  const [value, setValue] = useState('')
  const save = useMutation({
    mutationFn: (op: 'C' | 'U' | 'D') => catalogApi.saveExternalId({
      operation: op, entityType: 'TITLE', entityId: detail.title.titleId, provider, externalId: op === 'D' ? null : value.trim(),
    }),
    onSuccess: () => {
      toast.ok('외부 ID를 저장했습니다')
      setValue('')
      queryClient.invalidateQueries({ queryKey: ['title', detail.title.titleId] })
      queryClient.invalidateQueries({ queryKey: ['title-tmdb', detail.title.titleId] })
    },
    onError: toast.error,
  })
  const exists = detail.externalIds.some((e) => e.provider === provider)

  return (
    <Card title="외부 표준 ID" sub="TMDB는 영화·TV·인물 ID 공간이 달라 제공처를 나눕니다 · 같은 ID는 한 작품에만 붙습니다">
      {detail.externalIds.length === 0 ? <Empty>연결된 외부 ID가 없습니다</Empty> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>제공처</th><th>ID</th><th>연결 방법</th><th>확인</th></tr></thead>
            <tbody>
              {detail.externalIds.map((e) => {
                const href = providerLink(e.provider, e.externalId)
                return (
                  <tr key={e.provider}>
                    <td>{providerLabel[e.provider] ?? e.provider}</td>
                    <td className="mono">{href ? <a href={href} target="_blank" rel="noreferrer">{e.externalId} ↗</a> : e.externalId}</td>
                    <td><Badge tone={e.matchMethod === 'MANUAL' ? 'good' : 'plain'}>{e.matchMethod}</Badge></td>
                    <td>{e.verified ? <Badge tone="good">운영자 확인</Badge> : <Badge tone="warn">미확인</Badge>}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      <form className="toolbar" style={{ marginTop: 14 }} onSubmit={(e) => { e.preventDefault(); save.mutate(exists ? 'U' : 'C') }}>
        <label className="field">
          <span>제공처</span>
          <select value={provider} onChange={(e) => setProvider(e.target.value as Provider)}>
            {(['IMDB', 'KOBIS', 'TMDB_MOVIE', 'TMDB_TV'] as const).map((p) => <option key={p} value={p}>{providerLabel[p]}</option>)}
          </select>
        </label>
        <label className="field" style={{ flex: '1 1 160px' }}>
          <span>ID ({provider === 'IMDB' ? 'tt + 숫자 7자리 이상' : provider === 'KOBIS' ? '숫자 8자리' : '숫자'})</span>
          <input value={value} onChange={(e) => setValue(e.target.value)} className="mono" />
        </label>
        <button className="btn primary" type="submit" disabled={!value.trim() || save.isPending}>{exists ? '변경(U)' : '연결(C)'}</button>
        {exists && <button className="btn danger" type="button" disabled={save.isPending} onClick={() => save.mutate('D')}>연결 해제(D)</button>}
      </form>
    </Card>
  )
}

function LocalizationCard({ detail }: { detail: TitleDetail }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [lang, setLang] = useState('ko')
  const current = detail.localizations.find((l) => l.langCode === lang)
  const [title, setTitle] = useState(current?.localTitle ?? '')
  const [overview, setOverview] = useState(current?.overview ?? '')
  const pick = (code: string) => {
    const row = detail.localizations.find((l) => l.langCode === code)
    setLang(code)
    setTitle(row?.localTitle ?? '')
    setOverview(row?.overview ?? '')
  }
  const save = useMutation({
    mutationFn: () => catalogApi.saveLocalization({
      operation: current ? 'U' : 'C', titleId: detail.title.titleId, langCode: lang, localTitle: title, overview: overview || null,
    }),
    onSuccess: () => {
      toast.ok('현지화를 저장했습니다', '운영자 값(MANUAL)은 레거시 동기화가 덮지 않습니다.')
      queryClient.invalidateQueries({ queryKey: ['title', detail.title.titleId] })
    },
    onError: toast.error,
  })

  return (
    <Card title="언어별 제목·줄거리" sub="출처: LEGACY(구시스템), TMDB(반영), MANUAL(운영자 수정 — 동기화가 덮지 않음)">
      <div className="table-wrap">
        <table>
          <thead><tr><th>언어</th><th>제목</th><th>줄거리</th><th>출처</th><th /></tr></thead>
          <tbody>
            {detail.localizations.map((l) => (
              <tr key={l.langCode}>
                <td className="mono">{l.langCode}</td>
                <td><b>{l.localTitle}</b></td>
                <td className="wrap secondary" style={{ maxWidth: 520 }}>{l.overview ?? '-'}</td>
                <td><Badge tone={l.source === 'MANUAL' ? 'good' : l.source === 'TMDB' ? 'info' : 'plain'}>{l.source}</Badge></td>
                <td><button className="btn sm" onClick={() => pick(l.langCode)}>편집</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <form className="toolbar" style={{ marginTop: 14 }} onSubmit={(e) => { e.preventDefault(); save.mutate() }}>
        <label className="field" style={{ width: 100 }}>
          <span>언어 (BCP 47)</span>
          <input value={lang} onChange={(e) => pick(e.target.value.trim())} className="mono" />
        </label>
        <label className="field" style={{ flex: '1 1 200px' }}>
          <span>제목</span>
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label className="field" style={{ flex: '2 1 320px' }}>
          <span>줄거리</span>
          <input value={overview} onChange={(e) => setOverview(e.target.value)} />
        </label>
        <button className="btn primary" type="submit" disabled={!title.trim() || !lang || save.isPending}>{current ? '수정(U)' : '등록(C)'}</button>
      </form>
    </Card>
  )
}

export function TitleDetailPage() {
  const titleId = Number(useParams().titleId)
  const detail = useQuery({ queryKey: ['title', titleId], queryFn: () => catalogApi.detail(titleId), enabled: Number.isFinite(titleId) })
  const genres = useQuery({ queryKey: ['genres'], queryFn: catalogApi.genres, staleTime: 300_000 })
  const tmdb = useQuery({ queryKey: ['title-tmdb', titleId], queryFn: () => tmdbApi.title(titleId), enabled: detail.isSuccess, retry: false, staleTime: 300_000 })

  if (detail.isPending) return <Loading />
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
  const d = detail.data
  const t = d.title
  const credits = d.credits

  return (
    <>
      <div className="between">
        <Link to="/titles">← 작품 목록</Link>
        <span className="muted mono">title_id {t.titleId} · meta_version {t.metaVersion}</span>
      </div>

      <Hero detail={d} tmdb={tmdb.data} genreName={(code) => genres.data?.find((g) => g.genreCode === code)?.nameKo ?? code} />

      <TmdbPanel detail={d} tmdb={tmdb.data} loading={tmdb.isPending} error={tmdb.error} />

      <div className="grid grid-2">
        <Card title="메타 정보 (신규 스키마)">
          <dl className="meta-list" style={{ marginTop: 0 }}>
            <div><dt>원제</dt><dd>{t.originalTitle}</dd></div>
            <div><dt>원어</dt><dd>{t.originalLanguage ?? '-'}</dd></div>
            <div><dt>최초 공개일</dt><dd>{fmtDate(t.releaseDate)}</dd></div>
            <div><dt>상영 시간</dt><dd>{t.runtimeMin ? `${t.runtimeMin}분` : '-'}</dd></div>
            <div><dt>상태</dt><dd>{label(titleStatusLabel, t.status)}</dd></div>
            <div><dt>인기도</dt><dd>{t.popularity ?? '-'}</dd></div>
            <div><dt>평가 수</dt><dd>{fmtNumber(t.voteCount)}</dd></div>
            <div><dt>제작 국가</dt><dd>{d.countries.join(', ') || '-'}</dd></div>
          </dl>
          <h3 style={{ fontSize: 14, margin: '18px 0 8px' }}>지역별 공개</h3>
          {d.regions.length === 0 ? <Empty>지역 정보가 없습니다</Empty> : (
            <table>
              <thead><tr><th>지역</th><th>공개일</th><th>관람 등급</th><th>출처</th></tr></thead>
              <tbody>{d.regions.map((r) => (
                <tr key={r.regionCode}><td className="mono">{r.regionCode}</td><td>{fmtDate(r.releaseDate)}</td><td>{r.ageRating ?? '-'}</td><td>{r.source}</td></tr>
              ))}</tbody>
            </table>
          )}
        </Card>
        <ExternalIdCard detail={d} />
      </div>

      <LocalizationCard key={`loc-${t.titleId}-${d.localizations.length}`} detail={d} />

      {t.titleType === 'SERIES' && (
        <Card title="시즌" sub="에피소드 수는 원본 값, 적재 수는 실제 content_episode 행 수">
          {d.seasons.length === 0 ? <Empty>시즌이 없습니다 (품질 규칙 DQ005 대상)</Empty> : (
            <table>
              <thead><tr><th>시즌</th><th>이름</th><th>첫 방영</th><th className="num">에피소드 수</th><th className="num">적재된 회차</th><th>일치</th></tr></thead>
              <tbody>{d.seasons.map((s) => (
                <tr key={s.seasonId}>
                  <td>S{s.seasonNo}</td><td>{s.seasonName ?? '-'}</td><td>{fmtDate(s.airDate)}</td>
                  <td className="num">{s.episodeCount ?? '-'}</td><td className="num">{s.loadedEpisodes}</td>
                  <td>{s.episodeCount == null || s.episodeCount === s.loadedEpisodes ? <Badge tone="good">일치</Badge> : <Badge tone="warn">차이</Badge>}</td>
                </tr>
              ))}</tbody>
            </table>
          )}
        </Card>
      )}

      <Card title="출연·제작진 (신규 스키마)" sub="레거시는 이름 문자열만 있어 동명이인을 가를 수 없습니다 → 이름만 있는 인물로 표시">
        {credits.length === 0 ? <Empty>출연 정보가 없습니다</Empty> : (
          <table>
            <thead><tr><th>역할</th><th>이름</th><th>배역</th><th>인물 확인</th></tr></thead>
            <tbody>{credits.map((c) => (
              <tr key={`${c.personId}-${c.roleType}-${c.characterName}`}>
                <td>{roleLabel[c.roleType] ?? c.roleType}</td><td><b>{c.personName}</b></td><td>{c.characterName || '-'}</td>
                <td>{c.nameOnly ? <Badge tone="warn">이름만 있음</Badge> : <Badge tone="good">외부 ID 확인</Badge>}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </Card>
    </>
  )
}
