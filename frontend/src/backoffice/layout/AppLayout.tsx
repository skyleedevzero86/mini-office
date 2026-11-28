import { RobotOutlined } from '@ant-design/icons'
import { ThemedLayout } from '@refinedev/antd'
import { useLogout } from '@refinedev/core'
import { Button, Drawer, Layout, Space, Tag, Typography, theme } from 'antd'
import { Link, Outlet } from 'react-router'
import { ChatPanel } from '@/backoffice/components/chat/ChatPanel'
import { usePermission } from '@/backoffice/platform'
import { boPath } from '@/backoffice/routes'
import { ChatDrawerProvider, useChatDrawer } from '@/backoffice/store/chatDrawer'

function Title({ collapsed }: { collapsed: boolean }) {
  const { token } = theme.useToken()
  return (
    <Link to={boPath('/')} aria-label="대시보드로 가기" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <RobotOutlined style={{ fontSize: 22, color: token.colorPrimary }} />
      {!collapsed && (
        <Typography.Title level={5} style={{ margin: 0, whiteSpace: 'nowrap' }}>
          AI 백오피스
        </Typography.Title>
      )}
    </Link>
  )
}

function Header() {
  const { token } = theme.useToken()
  const { user, roles } = usePermission()
  const { toggle } = useChatDrawer()
  const { mutate: logout } = useLogout()
  return (
    <Layout.Header
      style={{
        background: token.colorBgElevated,
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
        gap: 12,
        padding: '0 24px',
        position: 'sticky',
        top: 0,
        zIndex: 10,
      }}
    >
      <Space size={4} wrap>
        {roles.map((r) => (
          <Tag key={r}>{r}</Tag>
        ))}
      </Space>
      <Typography.Text strong>{user?.name ?? ''}</Typography.Text>
      <Button type="primary" icon={<RobotOutlined />} onClick={toggle}>
        AI 채팅
      </Button>
      <Button onClick={() => logout()}>로그아웃</Button>
    </Layout.Header>
  )
}

function ChatDrawer() {
  const { open, setOpen } = useChatDrawer()
  return (
    <Drawer
      title="AI 운영 에이전트"
      placement="right"
      width={460}
      open={open}
      mask={false}
      onClose={() => setOpen(false)}
      styles={{ body: { display: 'flex', flexDirection: 'column', paddingBottom: 12 } }}
    >
      <ChatPanel />
    </Drawer>
  )
}

export function AppLayout() {
  return (
    <ChatDrawerProvider>
      <ThemedLayout
        Header={Header}
        Title={Title}
      >
        <Outlet />
      </ThemedLayout>
      <ChatDrawer />
    </ChatDrawerProvider>
  )
}
