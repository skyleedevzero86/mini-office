import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Select, Space, Table, Typography } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router'
import { governanceApi } from '@/backoffice/api/governance'
import type { AutomationCandidate } from '@/backoffice/api/types'
import { errorMessage } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import { fmtDateTime } from '@/backoffice/utils/time'

export function AutomationPage() {
  const [days, setDays] = useState(7)
  const candidates = useQuery({ queryKey: ['automation', days], queryFn: () => governanceApi.automationCandidates(days, 20) })

  return (
    <Card
      title="자동화 후보"
      extra={
        <Select
          value={days}
          onChange={setDays}
          style={{ width: 120 }}
          options={[7, 14, 30].map((d) => ({ value: d, label: `최근 ${d}일` }))}
        />
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">사람이 같은 작업을 여러 번 되풀이한 것을 감사 로그에서 모았습니다. 자주 하는 작업일수록 위에 있습니다.</Typography.Text>
        {candidates.error && <Alert type="error" message={errorMessage(candidates.error)} />}
        <Table<AutomationCandidate>
          rowKey="action"
          loading={candidates.isLoading}
          dataSource={candidates.data ?? []}
          pagination={false}
          locale={{ emptyText: '되풀이한 작업이 없습니다' }}
          columns={[
            { title: '작업', dataIndex: 'action', render: (v: string) => <Link to={boPath(`/audit?action=${encodeURIComponent(v)}&actorType=HUMAN`)}><code>{v}</code></Link> },
            { title: '횟수', dataIndex: 'count', width: 90, align: 'right' },
            { title: '사람 수', dataIndex: 'actorCount', width: 90, align: 'right' },
            { title: '마지막', dataIndex: 'lastAt', width: 150, render: (v: string) => fmtDateTime(v) },
            { title: '제안', dataIndex: 'suggestion' },
          ]}
        />
      </Space>
    </Card>
  )
}
