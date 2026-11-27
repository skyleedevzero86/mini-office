import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Alert, Card, Input, Select, Space, Table, Tag, Typography } from 'antd'
import { useNavigate, useSearchParams } from 'react-router'
import { contentApi } from '@/backoffice/api/content'
import type { TitleListItem } from '@/backoffice/api/types'
import { errorMessage } from '@/backoffice/platform'
import { label, titleTypeLabel } from '@/backoffice/utils/labels'
import { fmtDate } from '@/backoffice/utils/time'
import { boPath } from '@/backoffice/routes'

export function ContentListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const query = params.get('query') ?? ''
  const type = params.get('type') ?? ''
  const page = Number(params.get('page') ?? 0)
  const size = Number(params.get('size') ?? 20)

  const update = (patch: Record<string, string | number | null>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, String(v))
    }
    if (!('page' in patch)) next.delete('page')
    setParams(next)
  }

  const list = useQuery({
    queryKey: ['content', 'titles', { query, type, page, size }],
    queryFn: () => contentApi.page({ query: query || undefined, type: type || undefined, page, size }),
    placeholderData: keepPreviousData,
  })

  return (
    <Card title="콘텐츠">
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          defaultValue={query}
          placeholder="제목·원제 검색"
          style={{ width: 280 }}
          onSearch={(v) => update({ query: v.trim() })}
        />
        <Select
          allowClear
          placeholder="유형"
          style={{ width: 140 }}
          value={type || undefined}
          onChange={(v?: string) => update({ type: v ?? null })}
          options={[
            { value: 'MOVIE', label: '영화' },
            { value: 'SERIES', label: '시리즈' },
          ]}
        />
      </Space>
      {list.error && <Alert type="error" message={errorMessage(list.error)} style={{ marginBottom: 16 }} />}
      <Table<TitleListItem>
        rowKey="titleId"
        loading={list.isFetching}
        dataSource={list.data?.items ?? []}
        onRow={(r) => ({ onClick: () => navigate(boPath(`/contents/${r.titleId}`)), style: { cursor: 'pointer' } })}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: list.data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `전체 ${t.toLocaleString()}건`,
          onChange: (p, s) => update({ page: p - 1, size: s }),
        }}
        columns={[
          { title: 'ID', dataIndex: 'titleId', width: 90 },
          {
            title: '제목',
            render: (_, r) => (
              <Space direction="vertical" size={0}>
                <Typography.Text strong>{r.displayTitle}</Typography.Text>
                {r.originalTitle !== r.displayTitle && <Typography.Text type="secondary">{r.originalTitle}</Typography.Text>}
              </Space>
            ),
          },
          { title: '유형', dataIndex: 'titleType', width: 90, render: (v: string) => label(titleTypeLabel, v) },
          { title: '공개일', dataIndex: 'releaseDate', width: 120, render: (v: string | null) => fmtDate(v) },
          {
            title: '외부 ID',
            dataIndex: 'providers',
            render: (ps: string[]) => (ps.length ? ps.map((p) => <Tag key={p}>{p}</Tag>) : <Typography.Text type="secondary">없음</Typography.Text>),
          },
          { title: '장르', dataIndex: 'genres', render: (gs: string[]) => gs.join(', ') },
        ]}
      />
    </Card>
  )
}
