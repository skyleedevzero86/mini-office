import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Col, List, Row, Space, Statistic, Table, Tag, Typography } from 'antd'
import { Link } from 'react-router'
import { agentApi } from '@/backoffice/api/agent'
import { contentApi } from '@/backoffice/api/content'
import { governanceApi } from '@/backoffice/api/governance'
import { operationApi } from '@/backoffice/api/operation'
import type { QualityResultRow, Severity } from '@/backoffice/api/types'
import { StatusTag } from '@/backoffice/components/StatusTag'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { actorTypeColor, actorTypeLabel, auditResultColor, auditResultLabel, severityColor, severityLabel } from '@/backoffice/utils/labels'
import { fmtDateTime } from '@/backoffice/utils/time'
import { boPath } from '@/backoffice/routes'

const severities: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export function DashboardPage() {
  const { can } = usePermission()
  const canOps = can(['schedule:read', 'issue:read'])
  const canAudit = can('audit:read')
  const canContent = can('content:read')

  const pending = useQuery({ queryKey: ['dashboard', 'pending'], queryFn: governanceApi.pendingCount })
  const summary = useQuery({ queryKey: ['dashboard', 'summary'], queryFn: operationApi.summary, enabled: canOps })
  const agentRuns = useQuery({ queryKey: ['dashboard', 'agentRuns'], queryFn: governanceApi.recentAgentRuns, enabled: canAudit })
  const conversations = useQuery({ queryKey: ['agent', 'conversations'], queryFn: agentApi.conversations, enabled: !canAudit })
  const quality = useQuery({ queryKey: ['dashboard', 'quality'], queryFn: contentApi.latestQuality, enabled: canContent })

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        대시보드
      </Typography.Title>

      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card>
            <Link to={boPath('/approvals')}>
              <Statistic title="승인 대기" value={pending.data ?? '-'} loading={pending.isLoading} />
            </Link>
          </Card>
        </Col>
        {canOps && (
          <>
            <Col xs={12} md={6}>
              <Card>
                <Statistic title="오늘 편성" value={summary.data?.todaySlots ?? '-'} loading={summary.isLoading} />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card>
                <Statistic title="편성 초안" value={summary.data?.draftSlots ?? '-'} loading={summary.isLoading} />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card>
                <Statistic title="비노출 콘텐츠" value={summary.data?.hiddenContents ?? '-'} loading={summary.isLoading} />
              </Card>
            </Col>
          </>
        )}
      </Row>

      {summary.error && <Alert type="error" message={errorMessage(summary.error)} />}

      <Row gutter={[16, 16]}>
        {canOps && (
          <Col xs={24} md={12}>
            <Card title="열린 이슈 (심각도별)" extra={<Link to={boPath('/issues')}>이슈 보기</Link>}>
              <Space wrap size="large">
                {severities.map((s) => (
                  <Statistic
                    key={s}
                    title={<Tag color={severityColor[s]}>{severityLabel[s]}</Tag>}
                    value={summary.data?.openIssuesBySeverity?.[s] ?? 0}
                  />
                ))}
              </Space>
            </Card>
          </Col>
        )}

        <Col xs={24} md={12}>
          {canAudit ? (
            <Card title="최근 에이전트 실행" extra={<Link to={boPath('/audit?actorType=AGENT')}>감사 로그</Link>}>
              <List
                loading={agentRuns.isLoading}
                dataSource={agentRuns.data ?? []}
                locale={{ emptyText: '최근 실행이 없습니다' }}
                renderItem={(log) => (
                  <List.Item>
                    <Space wrap>
                      <StatusTag value={log.actorType} labels={actorTypeLabel} colors={actorTypeColor} />
                      <code>{log.action}</code>
                      <StatusTag value={log.result} labels={auditResultLabel} colors={auditResultColor} />
                      <Typography.Text type="secondary">{fmtDateTime(log.createdAt)}</Typography.Text>
                      {log.traceId && <Link to={boPath(`/audit?traceId=${encodeURIComponent(log.traceId)}`)}>흐름 보기</Link>}
                    </Space>
                  </List.Item>
                )}
              />
            </Card>
          ) : (
            <Card title="내 최근 대화">
              <List
                loading={conversations.isLoading}
                dataSource={(conversations.data ?? []).slice(0, 5)}
                locale={{ emptyText: '대화가 없습니다' }}
                renderItem={(c) => (
                  <List.Item>
                    {c.title || '제목 없음'} <Typography.Text type="secondary">{fmtDateTime(c.createdAt)}</Typography.Text>
                  </List.Item>
                )}
              />
            </Card>
          )}
        </Col>
      </Row>

      {canContent && (
        <Card title="content-meta 품질 점검 (최근)">
          {quality.error ? (
            <Alert type="error" message={errorMessage(quality.error)} />
          ) : !quality.isLoading && !quality.data ? (
            <Typography.Text type="secondary">아직 점검 결과가 없습니다.</Typography.Text>
          ) : (
            <Table<QualityResultRow>
              size="small"
              rowKey="ruleCode"
              loading={quality.isLoading}
              pagination={false}
              dataSource={quality.data?.rules ?? []}
              columns={[
                { title: '규칙', dataIndex: 'ruleCode', width: 90 },
                { title: '이름', dataIndex: 'ruleName' },
                { title: '위반', dataIndex: 'violationCount', width: 80, align: 'right' },
                { title: '기준', dataIndex: 'threshold', width: 80, align: 'right' },
                {
                  title: '결과',
                  dataIndex: 'result',
                  width: 80,
                  render: (r: string) => <Tag color={r === 'PASS' ? 'green' : 'red'}>{r === 'PASS' ? '통과' : '실패'}</Tag>,
                },
              ]}
            />
          )}
        </Card>
      )}
    </Space>
  )
}
