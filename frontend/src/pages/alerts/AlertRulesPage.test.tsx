import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { createAlertRule, fetchAlertRules } from '../../services/api'
import { fetchProjects } from '../../services/projectApi'
import { AlertRulesPage } from './AlertRulesPage'

vi.mock('../../services/api', () => ({
  createAlertRule: vi.fn(), deleteAlertRule: vi.fn(), fetchAlertRules: vi.fn(), toggleAlertRule: vi.fn(),
}))
vi.mock('../../services/projectApi', () => ({ fetchProjects: vi.fn() }))

describe('AlertRulesPage', () => {
  it('creates a validated project-scoped rule with level and event-type filters', async () => {
    vi.mocked(fetchProjects).mockResolvedValue([{ id: 'project-1', organizationId: 'org-1', key: 'queue', name: 'Queue', active: true, environments: [], retention: { defaultDays: 30, levelOverrides: {} }, settings: {}, services: [], recentIngestion: { eventsLast24Hours: 0, errorEventsLast24Hours: 0, lastReceivedAt: null }, createdAt: '', updatedAt: '' }])
    vi.mocked(fetchAlertRules).mockResolvedValue([])
    vi.mocked(createAlertRule).mockResolvedValue({ id: 'rule-1', projectId: 'project-1', name: 'Queue spike', enabled: true, levels: ['ERROR'], eventTypes: ['QUEUE_FAILED'], windowSeconds: 60, threshold: 10, cooldownSeconds: 300 })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><AlertRulesPage /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('button', { name: '+ Create Alert Rule' }))
    fireEvent.change(screen.getByPlaceholderText('e.g. Queue Service Error Spike'), { target: { value: 'Queue spike' } })
    fireEvent.change(screen.getByLabelText('Event types'), { target: { value: 'QUEUE_FAILED' } })

    expect(screen.getByLabelText('Levels')).toHaveValue('ERROR')
    expect(screen.getByLabelText('Window (sec)')).toHaveAttribute('min', '10')
    fireEvent.click(screen.getByRole('button', { name: 'Create Rule' }))

    await waitFor(() => expect(createAlertRule).toHaveBeenCalledWith('project-1', expect.objectContaining({
      name: 'Queue spike', levels: ['ERROR'], eventTypes: ['QUEUE_FAILED'], windowSeconds: 60, threshold: 10, cooldownSeconds: 300,
    })))
  })
})
