import '@ant-design/v5-patch-for-react-19'
import {
  AuditOutlined, CheckSquareOutlined, DashboardOutlined, ExclamationCircleOutlined, RobotOutlined, SafetyOutlined, ScheduleOutlined, VideoCameraOutlined,
} from '@ant-design/icons'
import { ErrorComponent, useNotificationProvider } from '@refinedev/antd'
import { Authenticated, Refine } from '@refinedev/core'
import routerProvider, { CatchAllNavigate, NavigateToResource } from '@refinedev/react-router'
import { QueryClient } from '@tanstack/react-query'
import { App as AntdApp, ConfigProvider } from 'antd'
import koKR from 'antd/locale/ko_KR'
import { Outlet, Route, Routes } from 'react-router'
import { AppLayout } from './layout/AppLayout'
import { ApiError, authProvider, createAccessControlProvider, gatewayDataProvider } from './platform'
import { boPath } from './routes'
import { ApprovalDetailPage } from './views/approvals/ApprovalDetailPage'
import { ApprovalListPage } from './views/approvals/ApprovalListPage'
import { AuditLogPage } from './views/audit/AuditLogPage'
import { AutomationPage } from './views/automation/AutomationPage'
import { ContentDetailPage } from './views/content/ContentDetailPage'
import { ContentListPage } from './views/content/ContentListPage'
import { DashboardPage } from './views/dashboard/DashboardPage'
import { IssueDetailPage } from './views/issues/IssueDetailPage'
import { IssueListPage } from './views/issues/IssueListPage'
import { LoginPage } from './views/login/LoginPage'
import { ToolPolicyPage } from './views/policies/ToolPolicyPage'
import { SchedulePage } from './views/schedule/SchedulePage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (count, error) => !(error instanceof ApiError && error.statusCode > 0 && error.statusCode < 500) && count < 1,
      refetchOnWindowFocus: false,
      staleTime: 10_000,
    },
  },
})

const accessControlProvider = createAccessControlProvider({
  contents: { list: 'content:read', show: 'content:read' },
  schedule: { list: 'schedule:read' },
  issues: { list: 'issue:read', show: 'issue:read' },
  approvals: { list: null, show: null },
  audit: { list: 'audit:read' },
  automation: { list: 'audit:read' },
  policies: { list: 'policy:write' },
})

const resources = [
  { name: 'dashboard', list: boPath('/'), meta: { label: '대시보드', icon: <DashboardOutlined /> } },
  { name: 'contents', list: boPath('/contents'), show: boPath('/contents/:id'), meta: { label: '콘텐츠', icon: <VideoCameraOutlined /> } },
  { name: 'schedule', list: boPath('/schedule'), meta: { label: '편성표', icon: <ScheduleOutlined /> } },
  { name: 'issues', list: boPath('/issues'), show: boPath('/issues/:id'), meta: { label: '이슈', icon: <ExclamationCircleOutlined /> } },
  { name: 'approvals', list: boPath('/approvals'), show: boPath('/approvals/:id'), meta: { label: '승인함', icon: <CheckSquareOutlined /> } },
  { name: 'audit', list: boPath('/audit'), meta: { label: '감사 로그', icon: <AuditOutlined /> } },
  { name: 'automation', list: boPath('/automation'), meta: { label: '자동화 후보', icon: <RobotOutlined /> } },
  { name: 'policies', list: boPath('/policies'), meta: { label: '툴 정책', icon: <SafetyOutlined /> } },
]

export default function BackofficeApp() {
  return (
    <ConfigProvider locale={koKR}>
      <AntdApp>
        <Refine
          routerProvider={routerProvider}
          dataProvider={gatewayDataProvider('/api')}
          authProvider={authProvider}
          accessControlProvider={accessControlProvider}
          notificationProvider={useNotificationProvider}
          resources={resources}
          options={{ disableTelemetry: true, syncWithLocation: false, reactQuery: { clientConfig: queryClient } }}
        >
          <Routes>
            <Route
              element={
                <Authenticated key="bo-protected" fallback={<CatchAllNavigate to={boPath('/login')} />}>
                  <AppLayout />
                </Authenticated>
              }
            >
              <Route index element={<DashboardPage />} />
              <Route path="contents" element={<ContentListPage />} />
              <Route path="contents/:id" element={<ContentDetailPage />} />
              <Route path="schedule" element={<SchedulePage />} />
              <Route path="issues" element={<IssueListPage />} />
              <Route path="issues/:id" element={<IssueDetailPage />} />
              <Route path="approvals" element={<ApprovalListPage />} />
              <Route path="approvals/:id" element={<ApprovalDetailPage />} />
              <Route path="audit" element={<AuditLogPage />} />
              <Route path="automation" element={<AutomationPage />} />
              <Route path="policies" element={<ToolPolicyPage />} />
              <Route path="*" element={<ErrorComponent />} />
            </Route>
            <Route
              element={
                <Authenticated key="bo-public" fallback={<Outlet />}>
                  <NavigateToResource resource="dashboard" />
                </Authenticated>
              }
            >
              <Route path="login" element={<LoginPage />} />
            </Route>
          </Routes>
        </Refine>
      </AntdApp>
    </ConfigProvider>
  )
}
