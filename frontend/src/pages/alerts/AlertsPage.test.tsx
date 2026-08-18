import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fetchAlertById, fetchAlerts } from '../../services/api'
import { fetchProjects } from '../../services/projectApi'
import { AlertsPage } from './AlertsPage'

vi.mock('../../services/api', () => ({
  acknowledgeAlert: vi.fn(),
  fetchAlertById: vi.fn(),
  fetchAlerts: vi.fn(),
  retryAlertNotification: vi.fn(),
}))
vi.mock('../../services/projectApi', () => ({ fetchProjects: vi.fn() }))

const occurrence = {
  id: 'occ-1', ruleId: 'rule-1', ruleName: 'Queue failures', projectId: 'project-1',
  triggeredAt: '2026-08-18T01:00:00Z', windowStart: '2026-08-18T00:59:00Z',
  windowEnd: '2026-08-18T01:00:00Z', observedValue: 12, threshold: 10,
  status: 'ACKNOWLEDGED' as const, deliveryStatus: 'FAILED' as const, attemptCount: 2,
  acknowledgedAt: '2026-08-18T01:05:00Z', acknowledgedBy: 'operator@example.com',
  lastAttemptAt: '2026-08-18T01:06:00Z', lastError: 'provider unavailable',
  deliveryAttempts: [{ attemptNumber: 2, provider: 'telegram', attemptedAt: '2026-08-18T01:06:00Z', status: 'FAILED' as const, errorSummary: 'provider unavailable' }],
}

describe('AlertsPage', () => {
  it('shows why an occurrence triggered, acknowledgement, and delivery history', async () => {
    vi.mocked(fetchProjects).mockResolvedValue([{ id: 'project-1', organizationId: 'org-1', key: 'queue', name: 'Queue', active: true, environments: [], retention: { defaultDays: 30, levelOverrides: {} }, settings: {}, services: [], recentIngestion: { eventsLast24Hours: 0, errorEventsLast24Hours: 0, lastReceivedAt: null }, createdAt: '', updatedAt: '' }])
    vi.mocked(fetchAlerts).mockResolvedValue([occurrence])
    vi.mocked(fetchAlertById).mockResolvedValue(occurrence)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><AlertsPage /></QueryClientProvider>)
    fireEvent.click(await screen.findByText('Queue failures'))

    await screen.findByText('12 events reached threshold 10')
    expect(screen.getByRole('region', { name: 'Alert occurrence detail' })).toHaveTextContent('12 events reached threshold 10')
    expect(screen.getByText(/operator@example.com at/)).toBeInTheDocument()
    expect(screen.getByText(/Attempt 2 via telegram/)).toBeInTheDocument()
    expect(screen.getByText('provider unavailable')).toBeInTheDocument()
    expect(fetchAlertById).toHaveBeenCalledWith('project-1', 'occ-1')
  })
})
