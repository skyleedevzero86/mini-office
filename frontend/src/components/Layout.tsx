import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { migrationApi, tmdbApi } from '@/api/endpoints'
import { settings } from '@/lib/settings'
import { Badge } from './ui'
import { SettingsDialog } from './SettingsDialog'

const nav = [
  { group: '콘텐츠', items: [
    { to: '/titles', label: '작품 목록' },
    { to: '/statistics', label: '통계' },
  ] },
  { group: '운영', items: [
    { to: '/', label: '대시보드', end: true },
    { to: '/monitoring', label: '모니터링' },
    { to: '/migration', label: '이관' },
    { to: '/bo', label: '백오피스' },
  ] },
]

const titles: Record<string, string> = {
  '/': '대시보드',
  '/titles': '작품 목록',
  '/statistics': '통계',
  '/monitoring': '모니터링',
  '/migration': '이관',
}

export function Layout() {
  const location = useLocation()
  const queryClient = useQueryClient()
  const [settingsOpen, setSettingsOpen] = useState(false)
  const hasToken = settings.adminToken() !== ''

  const readSource = useQuery({ queryKey: ['read-source'], queryFn: migrationApi.readSource, enabled: hasToken, retry: false })
  const tmdb = useQuery({ queryKey: ['tmdb-status'], queryFn: tmdbApi.status, retry: false, staleTime: 60_000 })

  const pageTitle = titles[location.pathname] ?? (location.pathname.startsWith('/titles/') ? '작품 상세'
    : location.pathname.startsWith('/migration/') ? '이관 작업 상세' : '')

  return (
    <div className="shell">
      <aside className="sidebar">
        <Link to="/" className="brand" aria-label="첫 화면으로 가기">
          <div className="brand-mark">CM</div>
          <div>
            <div className="brand-name">Content Meta</div>
            <div className="brand-sub">영상 메타데이터 콘솔</div>
          </div>
        </Link>
        <nav className="nav">
          {nav.map((g) => (
            <div key={g.group}>
              <div className="nav-group">{g.group}</div>
              {g.items.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end}>
                  <span className="dot" />
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span>MovieMbti 레거시 → 콘텐츠 메타</span>
          <span>Java 21 · Spring Boot · MyBatis · MySQL</span>
        </div>
      </aside>
      <div className="main">
        <header className="topbar">
          <div className="topbar-title">{pageTitle}</div>
          <div className="topbar-right">
            {tmdb.data && (tmdb.data.configured ? <Badge tone="good">TMDB 연결</Badge> : <Badge tone="warn">TMDB 키 없음</Badge>)}
            {readSource.data && (
              <Badge tone={readSource.data.readSource === 'NEW' ? 'good' : 'info'}>읽기 원천 {readSource.data.readSource}</Badge>
            )}
            <button className="btn sm" onClick={() => setSettingsOpen(true)}>
              {hasToken ? '운영 토큰 ✓' : '운영 토큰 입력'}
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
      {settingsOpen && (
        <SettingsDialog
          onClose={(saved) => {
            setSettingsOpen(false)
            if (saved) queryClient.invalidateQueries()
          }}
        />
      )}
    </div>
  )
}
