import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/DashboardPage'
import { MigrationPage } from './pages/MigrationPage'
import { MigrationRunPage } from './pages/MigrationRunPage'
import { MonitoringPage } from './pages/MonitoringPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { StatisticsPage } from './pages/StatisticsPage'
import { TitleDetailPage } from './pages/TitleDetailPage'
import { TitleListPage } from './pages/TitleListPage'

const BackofficeApp = lazy(() => import('./backoffice/BackofficeApp'))

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="bo/*" element={<Suspense fallback={null}><BackofficeApp /></Suspense>} />
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="titles" element={<TitleListPage />} />
          <Route path="titles/:titleId" element={<TitleDetailPage />} />
          <Route path="statistics" element={<StatisticsPage />} />
          <Route path="monitoring" element={<MonitoringPage />} />
          <Route path="migration" element={<MigrationPage />} />
          <Route path="migration/:runId" element={<MigrationRunPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
