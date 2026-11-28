import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ApiError } from '@/api/client'
import { fmtNumber } from '@/lib/format'

export function Card({ title, sub, actions, children, className = '' }: {
  title?: ReactNode
  sub?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-head">
          <div>
            {title && <h2>{title}</h2>}
            {sub && <div className="sub">{sub}</div>}
          </div>
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function Kpi({ label, value, foot, ratio }: { label: string; value: ReactNode; foot?: ReactNode; ratio?: number }) {
  return (
    <div className="card kpi">
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">{typeof value === 'number' ? fmtNumber(value) : value}</span>
      {ratio !== undefined && (
        <div className="meter" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(ratio * 100)}>
          <span style={{ width: `${Math.min(100, Math.max(0, ratio * 100))}%` }} />
        </div>
      )}
      {foot && <span className="kpi-foot">{foot}</span>}
    </div>
  )
}

export type Tone = 'good' | 'warn' | 'bad' | 'info' | 'plain'

const toneIcon: Record<Tone, string> = { good: '✓', warn: '!', bad: '✕', info: 'i', plain: '' }

export function Badge({ tone = 'plain', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`badge ${tone === 'plain' ? '' : tone}`}>
      {toneIcon[tone] && <span className="ico" aria-hidden>{toneIcon[tone]}</span>}
      {children}
    </span>
  )
}

export function Loading({ label = '불러오는 중' }: { label?: string }) {
  return (
    <div className="loading">
      <span className="spinner" aria-hidden /> {label}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

export function Notice({ tone, title, children, actions }: { tone: 'info' | 'warn' | 'bad' | 'good'; title: string; children?: ReactNode; actions?: ReactNode }) {
  return (
    <div className={`notice ${tone}`}>
      <div>
        <strong>{title}</strong>
        {children && <p>{children}</p>}
        {actions && <div className="actions">{actions}</div>}
      </div>
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (error instanceof ApiError) {
    if (error.code === 'M005') {
      return (
        <Notice tone="warn" title="아직 신규 메타로 읽기를 전환하지 않았습니다"
          actions={<Link className="btn primary sm" to="/migration">이관 화면으로</Link>}>
          카탈로그 조회는 원본·신규 대조가 0/0/0으로 끝나고 읽기 원천을 NEW로 바꾼 뒤에 열립니다. 백필 → 대조 → 읽기 전환을 먼저 진행하세요.
        </Notice>
      )
    }
    if (error.code === 'C401') {
      return (
        <Notice tone="warn" title="운영 토큰이 필요합니다">
          오른쪽 위 <b>운영 토큰</b>에 서버의 ADMIN_TOKEN 값을 넣으세요. (demo 프로필 기본값: demo-admin-token)
        </Notice>
      )
    }
    return (
      <Notice tone="bad" title={`[${error.code}] ${error.message}`} actions={onRetry && <button className="btn sm" onClick={onRetry}>다시 시도</button>}>
        {error.detail}
      </Notice>
    )
  }
  return (
    <Notice tone="bad" title="서버에 연결하지 못했습니다" actions={onRetry && <button className="btn sm" onClick={onRetry}>다시 시도</button>}>
      백엔드(기본 http://localhost:8086)가 떠 있는지 확인하세요. {error instanceof Error ? error.message : ''}
    </Notice>
  )
}

export function Pagination({ page, totalPages, total, size, onPage, onSize }: {
  page: number
  totalPages: number
  total: number
  size: number
  onPage: (page: number) => void
  onSize?: (size: number) => void
}) {
  if (total === 0) return null
  const window = 2
  const start = Math.max(0, Math.min(page - window, totalPages - (window * 2 + 1)))
  const end = Math.min(totalPages - 1, start + window * 2)
  const pages: number[] = []
  for (let p = start; p <= end; p++) pages.push(p)
  const from = page * size + 1
  const to = Math.min(total, (page + 1) * size)

  return (
    <div className="pager">
      <span className="secondary">
        전체 {fmtNumber(total)}건 중 {fmtNumber(from)}–{fmtNumber(to)}
      </span>
      <div className="pager-pages" role="navigation" aria-label="페이지">
        <button className="btn sm" disabled={page === 0} onClick={() => onPage(0)} aria-label="첫 페이지">«</button>
        <button className="btn sm" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="이전 페이지">‹</button>
        {pages.map((p) => (
          <button key={p} className={`btn sm ${p === page ? 'current' : ''}`} onClick={() => onPage(p)} aria-current={p === page ? 'page' : undefined}>
            {p + 1}
          </button>
        ))}
        <button className="btn sm" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)} aria-label="다음 페이지">›</button>
        <button className="btn sm" disabled={page >= totalPages - 1} onClick={() => onPage(totalPages - 1)} aria-label="마지막 페이지">»</button>
      </div>
      {onSize && (
        <select value={size} onChange={(e) => onSize(Number(e.target.value))} aria-label="페이지 크기">
          {[10, 20, 50, 100].map((s) => <option key={s} value={s}>{s}개씩</option>)}
        </select>
      )}
    </div>
  )
}

export function Poster({ url, alt, small }: { url: string | null; alt: string; small?: boolean }) {
  return (
    <div className={`poster ${small ? 'poster-sm' : ''}`}>
      {url ? <img src={url} alt={alt} loading="lazy" /> : <span>{small ? '—' : '포스터 없음'}</span>}
    </div>
  )
}
