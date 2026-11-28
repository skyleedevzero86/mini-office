import { useState } from 'react'
import { settings } from '@/lib/settings'

export function SettingsDialog({ onClose }: { onClose: (saved: boolean) => void }) {
  const [token, setToken] = useState(settings.adminToken())
  const [worker, setWorker] = useState(settings.worker())

  return (
    <div className="dialog-backdrop" onClick={() => onClose(false)}>
      <form
        className="dialog"
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault()
          settings.save(token, worker)
          onClose(true)
        }}
      >
        <h2>운영 설정</h2>
        <p>운영 API(/api/admin/**)는 서버의 ADMIN_TOKEN과 같은 값을 X-Admin-Token 헤더로 보내야 열립니다. 값은 이 브라우저에만 저장됩니다.</p>
        <label className="field">
          <span>운영 토큰</span>
          <input type="password" value={token} onChange={(e) => setToken(e.target.value)} placeholder="demo-admin-token" autoFocus />
        </label>
        <label className="field">
          <span>작업자 이름 (작업 로그에 남음)</span>
          <input value={worker} onChange={(e) => setWorker(e.target.value)} placeholder="console" />
        </label>
        <div className="btn-row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn" onClick={() => onClose(false)}>취소</button>
          <button type="submit" className="btn primary">저장</button>
        </div>
      </form>
    </div>
  )
}
