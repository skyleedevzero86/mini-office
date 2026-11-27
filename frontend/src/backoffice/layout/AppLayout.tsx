import { RobotOutlined } from '@ant-design/icons'
import { ThemedLayout, ThemedTitle } from '@refinedev/antd'
import { useLogout } from '@refinedev/core'
import { Button, Drawer, Layout, Space, Tag, Typography, theme } from 'antd'
import { Outlet } from 'react-router'
import { ChatPanel } from '@/backoffice/components/chat/ChatPanel'
import { usePermission } from '@/backoffice/platform'
import { ChatDrawerProvider, useChatDrawer } from '@/backoffice/store/chatDrawer'

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
        Title={({ collapsed }) => <ThemedTitle collapsed={collapsed} text="AI 백오피스" icon={<RobotOutlined />} />}
      >
        <Outlet />
      </ThemedLayout>
      <ChatDrawer />
    </ChatDrawerProvider>
  )
}
