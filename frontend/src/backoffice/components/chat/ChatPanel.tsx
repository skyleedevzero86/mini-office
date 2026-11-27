import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Input, Select, Space, Tag, Typography } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { agentApi } from '@/backoffice/api/agent'
import { useAgentChat, type ChatItem, type ToolStatus } from '@/backoffice/hooks/useAgentChat'
import { useChatDrawer } from '@/backoffice/store/chatDrawer'
import { fmtDateTime } from '@/backoffice/utils/time'
import { label, toolLabel } from '@/backoffice/utils/labels'
import './chat.css'
import { boPath } from '@/backoffice/routes'

const toolStatus: Record<ToolStatus, { text: string; color: string }> = {
  RUNNING: { text: '실행 중', color: 'processing' },
  OK: { text: '성공', color: 'green' },
  DENIED: { text: '권한 없음', color: 'red' },
  PENDING_APPROVAL: { text: '승인 대기', color: 'gold' },
  ERROR: { text: '오류', color: 'red' },
}

function ToolCard({ item }: { readonly item: Extract<ChatItem, { kind: 'tool' }> }) {
  const s = toolStatus[item.status]
  return (
    <Card size="small" className="chat-tool" title={<span>툴 호출 · <code>{item.name}</code></span>} extra={<Tag color={s.color}>{s.text}</Tag>}>
      <pre className="chat-args">{JSON.stringify(item.args ?? {}, null, 2)}</pre>
      {item.code && item.status !== 'OK' && (
        <Typography.Text type="danger">
          {item.code}
          {item.message ? ` · ${item.message}` : ''}
        </Typography.Text>
      )}
      {item.latencyMs !== undefined && <Typography.Text type="secondary"> {item.latencyMs}ms</Typography.Text>}
    </Card>
  )
}

function ApprovalCard({ item, onGo }: { readonly item: Extract<ChatItem, { kind: 'approval' }>; readonly onGo: () => void }) {
  return (
    <Alert
      type="warning"
      showIcon
      message={`승인 요청 #${item.approvalId} · ${label(toolLabel, item.toolName)}`}
      description={
        <Space direction="vertical" size={4}>
          {item.reason && <span>이유: {item.reason}</span>}
          <span>승인자가 승인하면 실행합니다.</span>
          <Link to={boPath(`/approvals/${item.approvalId}`)} onClick={onGo}>
            승인함에서 보기
          </Link>
        </Space>
      }
    />
  )
}

function ItemView({ item, onGo }: { readonly item: ChatItem; readonly onGo: () => void }) {
  switch (item.kind) {
    case 'user':
      return <div className="chat-bubble chat-user">{item.text}</div>
    case 'assistant':
      return (
        <div className="chat-bubble chat-assistant">
          {item.text}
          {item.streaming && <span className="chat-caret">▍</span>}
        </div>
      )
    case 'tool':
      return <ToolCard item={item} />
    case 'approval':
      return <ApprovalCard item={item} onGo={onGo} />
    case 'error':
      return <Alert type="error" showIcon message={item.message} description={item.code} />
    case 'done':
      return (
        <Typography.Text type="secondary" className="chat-done">
          툴 {item.toolCalls}번 · 입력 {item.inputTokens} / 출력 {item.outputTokens} 토큰
        </Typography.Text>
      )
  }
}

export function ChatPanel() {
  const chat = useAgentChat()
  const { setOpen } = useChatDrawer()
  const [draft, setDraft] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)
  const conversations = useQuery({ queryKey: ['agent', 'conversations'], queryFn: agentApi.conversations })

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [chat.items])

  const submit = () => {
    const text = draft
    setDraft('')
    void chat.send(text).then(() => conversations.refetch())
  }

  return (
    <div className="chat-panel">
      <Space.Compact style={{ width: '100%' }}>
        <Select
          style={{ flex: 1 }}
          placeholder="대화 고르기"
          value={chat.conversationId ?? undefined}
          onChange={(v: string) => chat.selectConversation(v)}
          loading={conversations.isLoading}
          options={(conversations.data ?? []).map((c) => ({ value: c.id, label: `${c.title || '제목 없음'} · ${fmtDateTime(c.createdAt)}` }))}
        />
        <Button onClick={() => chat.selectConversation(null)}>새 대화</Button>
      </Space.Compact>

      <div className="chat-log">
        {chat.loadError && <Alert type="error" message={chat.loadError} />}
        {chat.items.length === 0 && !chat.loadError && (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <span>
                예: “다음 주 금요일 20시 tvN 편성 충돌 확인해줘”
                <br />
                위험한 변경은 승인 요청으로 올라갑니다.
              </span>
            }
          />
        )}
        {chat.items.map((item) => (
          <ItemView key={item.id} item={item} onGo={() => setOpen(false)} />
        ))}
        <div ref={bottomRef} />
      </div>

      <div className="chat-input">
        <Input.TextArea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          autoSize={{ minRows: 2, maxRows: 6 }}
          placeholder="운영 요청을 적습니다 (Enter 전송, Shift+Enter 줄바꿈)"
          onPressEnter={(e) => {
            if (e.shiftKey || e.nativeEvent.isComposing) return
            e.preventDefault()
            submit()
          }}
          disabled={chat.sending}
        />
        {chat.sending ? (
          <Button danger onClick={chat.stop}>
            멈추기
          </Button>
        ) : (
          <Button type="primary" onClick={submit} disabled={!draft.trim()}>
            보내기
          </Button>
        )}
      </div>
    </div>
  )
}
