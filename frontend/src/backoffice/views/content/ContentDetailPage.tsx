import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, Form, Image, Input, InputNumber, List, Select, Space, Table, Tag, Typography } from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { contentApi } from '@/backoffice/api/content'
import { operationApi } from '@/backoffice/api/operation'
import type { ExternalIdRow, LocalizationRow, Provider, TmdbCandidate } from '@/backoffice/api/types'
import { RequestApprovalModal } from '@/backoffice/components/RequestApprovalModal'
import { errorMessage, usePermission } from '@/backoffice/platform'
import { label, titleTypeLabel } from '@/backoffice/utils/labels'
import { fmtDate } from '@/backoffice/utils/time'
import { boPath } from '@/backoffice/routes'

const providers: Provider[] = ['TMDB_MOVIE', 'TMDB_TV', 'IMDB', 'KOBIS']

interface LinkTarget {
  provider: Provider
  externalId: string
}

export function ContentDetailPage() {
  const titleId = Number(useParams().id)
  const { can } = usePermission()
  const canWrite = can('content:write')
  const [target, setTarget] = useState<LinkTarget | null>(null)
  const [search, setSearch] = useState<{ query: string; year?: number } | null>(null)

  const detail = useQuery({ queryKey: ['content', 'title', titleId], queryFn: () => contentApi.detail(titleId), enabled: titleId > 0 })
  const tmdb = useQuery({ queryKey: ['content', 'tmdb', titleId], queryFn: () => contentApi.tmdb(titleId), enabled: titleId > 0 })
  const exposure = useQuery({
    queryKey: ['exposure', titleId],
    queryFn: () => operationApi.exposure(titleId),
    enabled: titleId > 0 && can('issue:read'),
  })
  const titleType = detail.data?.title.titleType ?? 'MOVIE'
  const candidates = useQuery({
    queryKey: ['content', 'tmdbSearch', titleType, search],
    queryFn: () => contentApi.searchTmdb(titleType, search!.query, search!.year),
    enabled: search !== null && search.query !== '',
  })

  if (detail.error) return <Alert type="error" message={errorMessage(detail.error)} />
  const d = detail.data
  const existing = (p: Provider) => d?.externalIds.find((x) => x.provider === p)
  const openProviders = providers.filter((p) => !existing(p))

  const startSearch = () => {
    if (!d) return
    const year = d.title.releaseDate ? Number(d.title.releaseDate.slice(0, 4)) : undefined
    setSearch({ query: d.title.originalTitle, year })
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card loading={detail.isLoading} title={<span>#{titleId} {d?.title.originalTitle}</span>} extra={<Link to={boPath('/contents')}>목록</Link>}>
        {d && (
          <Space align="start" size="large" wrap>
            {tmdb.data?.posterUrl && <Image src={tmdb.data.posterUrl} width={120} alt="포스터" />}
            <Descriptions column={{ xs: 1, md: 2 }} size="small">
              <Descriptions.Item label="유형">{label(titleTypeLabel, d.title.titleType)}</Descriptions.Item>
              <Descriptions.Item label="원어">{d.title.originalLanguage ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="공개일">{fmtDate(d.title.releaseDate)}</Descriptions.Item>
              <Descriptions.Item label="상영시간">{d.title.runtimeMin ? `${d.title.runtimeMin}분` : '-'}</Descriptions.Item>
              <Descriptions.Item label="상태">{d.title.status}</Descriptions.Item>
              <Descriptions.Item label="장르">{d.genres.join(', ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="노출">
                {exposure.data ? (
                  exposure.data.exposed ? (
                    <Tag color="green">노출</Tag>
                  ) : (
                    <Space>
                      <Tag color="red">비노출</Tag>
                      {exposure.data.reason}
                      {exposure.data.issueId && <Link to={boPath(`/issues/${exposure.data.issueId}`)}>이슈 #{exposure.data.issueId}</Link>}
                    </Space>
                  )
                ) : (
                  '-'
                )}
              </Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </Card>

      <Card title="외부 ID">
        <Table<ExternalIdRow>
          size="small"
          rowKey="provider"
          pagination={false}
          dataSource={d?.externalIds ?? []}
          locale={{ emptyText: '연결된 외부 ID가 없습니다' }}
          columns={[
            { title: '제공처', dataIndex: 'provider', width: 140 },
            { title: '외부 ID', dataIndex: 'externalId' },
            { title: '매칭', dataIndex: 'matchMethod', width: 120 },
            { title: '검증', dataIndex: 'verified', width: 80, render: (v: boolean) => (v ? <Tag color="green">검증</Tag> : <Tag>미검증</Tag>) },
          ]}
        />
        {canWrite && (
          <Form<LinkTarget>
            layout="inline"
            style={{ marginTop: 16 }}
            onFinish={(v) => setTarget({ provider: v.provider, externalId: v.externalId.trim() })}
          >
            <Form.Item name="provider" rules={[{ required: true, message: '제공처' }]}>
              <Select
                placeholder={openProviders.length ? '제공처' : '모두 연결됨'}
                disabled={!openProviders.length}
                style={{ width: 140 }}
                options={openProviders.map((p) => ({ value: p, label: p }))}
              />
            </Form.Item>
            <Form.Item name="externalId" rules={[{ required: true, message: '외부 ID' }]}>
              <Input placeholder="외부 ID" style={{ width: 200 }} />
            </Form.Item>
            <Button htmlType="submit" disabled={!openProviders.length}>
              직접 연결 요청
            </Button>
          </Form>
        )}
        {canWrite && (
          <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
            이미 연결된 외부 ID는 바꾸지 않습니다. 비어 있는 제공처만 연결을 요청할 수 있습니다.
          </Typography.Paragraph>
        )}
      </Card>

      <Card
        title="TMDB 후보"
        extra={
          <Space>
            <Button onClick={startSearch} disabled={!d}>
              원제·연도로 찾기
            </Button>
          </Space>
        }
      >
        {tmdb.data?.linked && (
          <Alert
            type="info"
            style={{ marginBottom: 12 }}
            message={`이미 연결됨: ${tmdb.data.provider} ${tmdb.data.tmdbId} · ${tmdb.data.title ?? ''}`}
          />
        )}
        {search && (
          <Space style={{ marginBottom: 12 }} wrap>
            <Input.Search
              defaultValue={search.query}
              style={{ width: 280 }}
              onSearch={(q) => setSearch({ ...search, query: q.trim() })}
              placeholder="검색어"
            />
            <InputNumber
              placeholder="연도"
              value={search.year}
              onChange={(y) => setSearch({ ...search, year: y ?? undefined })}
              min={1900}
              max={2100}
            />
          </Space>
        )}
        {candidates.error && <Alert type="error" message={errorMessage(candidates.error)} />}
        <List<TmdbCandidate>
          loading={candidates.isFetching}
          dataSource={candidates.data ?? []}
          locale={{ emptyText: search ? '후보가 없습니다' : '“원제·연도로 찾기”를 누릅니다' }}
          renderItem={(c) => {
            const linkedElsewhere = c.linkedTitleId !== null && c.linkedTitleId !== titleId
            const linkedHere = c.linkedTitleId === titleId
            const providerTaken = !linkedHere && Boolean(existing(c.provider))
            return (
              <List.Item
                actions={
                  canWrite
                    ? [
                        <Button
                          key="link"
                          size="small"
                          disabled={linkedHere || providerTaken || linkedElsewhere}
                          onClick={() => setTarget({ provider: c.provider, externalId: String(c.tmdbId) })}
                        >
                          {linkedHere ? '연결됨' : providerTaken ? '다른 ID 연결됨' : '연결 요청'}
                        </Button>,
                      ]
                    : []
                }
              >
                <List.Item.Meta
                  avatar={c.posterUrl ? <Image src={c.posterUrl} width={48} preview={false} alt="" /> : undefined}
                  title={
                    <Space wrap>
                      {c.title ?? c.originalTitle}
                      <Typography.Text type="secondary">{c.originalTitle}</Typography.Text>
                      <Tag>{c.provider} {c.tmdbId}</Tag>
                      {c.date && <Tag>{c.date}</Tag>}
                      {linkedElsewhere && <Link to={boPath(`/contents/${c.linkedTitleId}`)}>다른 작품(#{c.linkedTitleId})에 연결됨</Link>}
                    </Space>
                  }
                  description={<Typography.Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>{c.overview}</Typography.Paragraph>}
                />
              </List.Item>
            )
          }}
        />
      </Card>

      <Card title="현지화">
        <Table<LocalizationRow>
          size="small"
          rowKey="langCode"
          pagination={false}
          dataSource={d?.localizations ?? []}
          columns={[
            { title: '언어', dataIndex: 'langCode', width: 80 },
            { title: '제목', dataIndex: 'localTitle', width: 240 },
            { title: '줄거리', dataIndex: 'overview', render: (v: string | null) => <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>{v}</Typography.Paragraph> },
            { title: '출처', dataIndex: 'source', width: 100 },
          ]}
        />
      </Card>

      {target && (
        <RequestApprovalModal
          open
          toolName="content_link_external_id"
          title="외부 ID 연결 승인 요청"
          args={{
            entityType: 'TITLE',
            entityId: titleId,
            provider: target.provider,
            externalId: target.externalId,
          }}
          onClose={() => setTarget(null)}
        />
      )}
    </Space>
  )
}
