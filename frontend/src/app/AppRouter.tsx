import { Navigate, Route, Routes } from 'react-router-dom'
import { Navigation } from '../components/Navigation'
import { HomePage } from '../pages/HomePage'
import { LogExplorerPage } from '../pages/logs/LogExplorerPage'
import { AnalyticsPage } from '../pages/analytics/AnalyticsPage'
import { LiveTailPage } from '../pages/livetail/LiveTailPage'
import { AlertRulesPage } from '../pages/alerts/AlertRulesPage'
import { AlertsPage } from '../pages/alerts/AlertsPage'
import { OrganizationPage } from '../pages/organization/OrganizationPage'
import { ProjectsPage } from '../pages/projects/ProjectsPage'

export function AppRouter() {
  return (
    <div className="min-h-screen flex flex-col">
      <Navigation />
      <div className="flex-1">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/logs" element={<LogExplorerPage />} />
          <Route path="/dashboard" element={<AnalyticsPage />} />
          <Route path="/live-tail" element={<LiveTailPage />} />
          <Route path="/alerts/rules" element={<AlertRulesPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/organization" element={<OrganizationPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </div>
  )
}
