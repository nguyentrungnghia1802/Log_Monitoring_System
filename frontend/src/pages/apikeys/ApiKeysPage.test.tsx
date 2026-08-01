import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchProjects } from '../../services/projectApi'
import { createApiKey, fetchApiKeys, revokeApiKey, rotateApiKey } from '../../services/apiKeyApi'
import { ApiKeysPage } from './ApiKeysPage'

vi.mock('../../services/projectApi', () => ({
  fetchProjects: vi.fn(),
}))

vi.mock('../../services/apiKeyApi', () => ({
  createApiKey: vi.fn(),
  fetchApiKeys: vi.fn(),
  revokeApiKey: vi.fn(),
  rotateApiKey: vi.fn(),
}))

const project = {
  id: 'project-1',
  organizationId: 'org-1',
  key: 'checkout',
  name: 'Checkout',
  active: true,
  environments: ['production'],
  retention: { defaultDays: 7, levelOverrides: {} },
  settings: {},
  services: [],
  recentIngestion: { eventsLast24Hours: 0, errorEventsLast24Hours: 0, lastReceivedAt: null },
  createdAt: '2026-08-02T08:00:00Z',
  updatedAt: '2026-08-02T08:00:00Z',
}

const metadata = {
  id: 'key-1',
  projectId: 'project-1',
  name: 'production collector',
  publicId: 'ak_public-id',
  secretLast4: 'wxyz',
  status: 'ACTIVE',
  createdAt: '2026-08-02T08:00:00Z',
  lastUsedAt: null,
  revokedAt: null,
}

const secretResponse = {
  ...metadata,
  rawApiKey: 'lm_live_ak_public-id_a-secret-that-is-only-visible-once',
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><ApiKeysPage /></QueryClientProvider>)
  return queryClient
}

describe('ApiKeysPage', () => {
  beforeEach(() => {
    vi.mocked(fetchProjects).mockResolvedValue([project])
    vi.mocked(fetchApiKeys).mockResolvedValue([metadata])
    vi.mocked(createApiKey).mockResolvedValue(secretResponse)
    vi.mocked(rotateApiKey).mockResolvedValue({ ...secretResponse, name: 'rotated collector' })
    vi.mocked(revokeApiKey).mockResolvedValue(undefined)
    localStorage.clear()
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  it('renders metadata without rendering or persisting the raw secret', async () => {
    renderPage()

    expect(await screen.findByText('production collector')).toBeInTheDocument()
    expect(screen.getByText('••••wxyz')).toBeInTheDocument()
    expect(screen.queryByText(secretResponse.rawApiKey)).not.toBeInTheDocument()
    expect(localStorage.getItem('rawApiKey')).toBeNull()
  })

  it('shows a one-time secret with copy confirmation and clears it when dismissed', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
    renderPage()

    fireEvent.change(await screen.findByLabelText('Key name'), { target: { value: 'new collector' } })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create API key' })).not.toBeDisabled())
    fireEvent.click(screen.getByRole('button', { name: 'Create API key' }))

    expect(await screen.findByTestId('one-time-api-key')).toHaveTextContent(secretResponse.rawApiKey)
    expect(screen.getByText('It will not be shown again. Store it in your secret manager.')).toBeInTheDocument()
    expect(localStorage.getItem('rawApiKey')).toBeNull()
    expect(createApiKey).toHaveBeenCalledWith('project-1', 'new collector')

    fireEvent.click(screen.getByRole('button', { name: 'Copy secret' }))
    expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'I stored it safely' }))
    await waitFor(() => expect(screen.queryByTestId('one-time-api-key')).not.toBeInTheDocument())
  })

  it('requires confirmation before rotation and revocation', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderPage()

    expect(await screen.findByText('production collector')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Rotate' }))
    fireEvent.click(screen.getByRole('button', { name: 'Revoke' }))

    expect(confirm).toHaveBeenCalledTimes(2)
    expect(rotateApiKey).not.toHaveBeenCalled()
    expect(revokeApiKey).not.toHaveBeenCalled()
  })
})
