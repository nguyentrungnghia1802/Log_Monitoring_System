import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fetchProjects, updateProjectRetention } from '../../services/projectApi'
import { ProjectsPage } from './ProjectsPage'

vi.mock('../../services/projectApi', () => ({
  createProject: vi.fn(),
  deactivateProject: vi.fn(),
  fetchProjects: vi.fn(),
  updateProject: vi.fn(),
  updateProjectRetention: vi.fn(),
}))

describe('ProjectsPage', () => {
  it('shows an actionable empty state when no project is authorized', async () => {
    vi.mocked(fetchProjects).mockResolvedValue([])
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(
      <QueryClientProvider client={queryClient}>
        <ProjectsPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('No projects found. Create the first monitored application above.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create project' })).toBeInTheDocument()
  })

  it('edits default and per-level retention with explicit TTL semantics', async () => {
    vi.mocked(fetchProjects).mockResolvedValue([{
      id: 'project-1', organizationId: 'org-1', key: 'checkout', name: 'Checkout', active: true,
      environments: ['production'], retention: { defaultDays: 7, levelOverrides: { ERROR: 30 } },
      settings: {}, services: ['checkout'], recentIngestion: { eventsLast24Hours: 100, errorEventsLast24Hours: 2, lastReceivedAt: null },
      createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
    }])
    vi.mocked(updateProjectRetention).mockResolvedValue(undefined as never)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><ProjectsPage /></QueryClientProvider>)

    const retentionForm = (await screen.findByRole('heading', { name: 'Retention' })).closest('form')
    expect(retentionForm).toHaveTextContent(/existing expireAt values are not backfilled/i)
    await waitFor(() => expect(screen.getByLabelText('ERROR retention days')).toHaveValue(30))
    fireEvent.change(screen.getAllByLabelText('Default retention (days)')[1], { target: { value: '14' } })
    fireEvent.change(screen.getByLabelText('WARN retention days'), { target: { value: '21' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save retention' }))

    await waitFor(() => expect(updateProjectRetention).toHaveBeenCalledWith('project-1', {
      defaultDays: 14,
      levelOverrides: { WARN: 21, ERROR: 30 },
    }))
    expect(screen.getByText(/2.0×/)).toBeInTheDocument()
  })
})
