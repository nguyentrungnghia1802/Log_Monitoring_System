import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fetchProjects } from '../../services/projectApi'
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
})
