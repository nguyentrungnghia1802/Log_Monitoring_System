import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { OrganizationPage } from './OrganizationPage'
import * as organizationApi from '../../services/organizationApi'

vi.mock('../../services/organizationApi', () => ({
  fetchCurrentOrganization: vi.fn(),
  fetchOrganizationMembers: vi.fn(),
  inviteOrganizationMember: vi.fn(),
  removeOrganizationMember: vi.fn(),
  updateCurrentOrganization: vi.fn(),
  updateOrganizationMember: vi.fn(),
}))

describe('OrganizationPage', () => {
  it('renders organization settings and an empty-member state', async () => {
    vi.mocked(organizationApi.fetchCurrentOrganization).mockResolvedValue({
      id: 'org-1', slug: 'acme', name: 'Acme', active: true, settings: {}, memberCount: 0,
    })
    vi.mocked(organizationApi.fetchOrganizationMembers).mockResolvedValue([])

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><OrganizationPage /></QueryClientProvider>)

    expect(await screen.findByText('Organization settings')).toBeInTheDocument()
    expect(screen.getByText('No organization members found.')).toBeInTheDocument()
  })
})
