import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '../auth/ProtectedRoute'
import { OperatorRoute } from '../auth/OperatorRoute'
import { LoginPage } from '../pages/auth/LoginPage'
import { HomePage } from '../pages/HomePage'
import { LogExplorerPage } from '../pages/logs/LogExplorerPage'
import { AnalyticsPage } from '../pages/analytics/AnalyticsPage'
import { LiveTailPage } from '../pages/livetail/LiveTailPage'
import { AlertRulesPage } from '../pages/alerts/AlertRulesPage'
import { AlertsPage } from '../pages/alerts/AlertsPage'
import { OrganizationPage } from '../pages/organization/OrganizationPage'
import { ProjectsPage } from '../pages/projects/ProjectsPage'
import { ApiKeysPage } from '../pages/apikeys/ApiKeysPage'
import { PlatformHealthPage } from '../pages/system/PlatformHealthPage'

export function AppRouter() {
  return (
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/logs" element={<LogExplorerPage />} />
          <Route path="/dashboard" element={<AnalyticsPage />} />
          <Route path="/live-tail" element={<LiveTailPage />} />
          <Route path="/alerts/rules" element={<AlertRulesPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/organization" element={<OrganizationPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/api-keys" element={<ApiKeysPage />} />
          <Route element={<OperatorRoute />}>
            <Route path="/system-health" element={<PlatformHealthPage />} />
          </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
  )
}
