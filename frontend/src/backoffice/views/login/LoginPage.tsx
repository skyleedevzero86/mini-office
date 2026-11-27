import { RobotOutlined } from '@ant-design/icons'
import { useLogin } from '@refinedev/core'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'

interface LoginForm {
  email: string
  password: string
}

export function LoginPage() {
  const { mutate: login, isPending, data } = useLogin<LoginForm>()
  const failed = data && !data.success ? (data.error?.message ?? '로그인하지 못했습니다.') : null

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Typography.Title level={4} style={{ marginTop: 0 }}>
          <RobotOutlined /> AI 백오피스
        </Typography.Title>
        <Typography.Paragraph type="secondary">운영 계정으로 로그인합니다.</Typography.Paragraph>
        {failed && <Alert type="error" message={failed} style={{ marginBottom: 16 }} />}
        <Form<LoginForm> layout="vertical" onFinish={(v) => login(v)} requiredMark={false}>
          <Form.Item name="email" label="이메일" rules={[{ required: true, type: 'email', message: '이메일을 적어 주세요.' }]}>
            <Input autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item name="password" label="비밀번호" rules={[{ required: true, message: '비밀번호를 적어 주세요.' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={isPending}>
            로그인
          </Button>
        </Form>
      </Card>
    </div>
  )
}
