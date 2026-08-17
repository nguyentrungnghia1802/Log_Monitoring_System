import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fetchLogs } from '../../services/api'
import { LogExplorerPage } from './LogExplorerPage'

vi.mock('../../services/api', () => ({ fetchLogs: vi.fn() }))

describe('LogExplorerPage', () => {
  it('searches scoped logs and opens event details', async () => {
    vi.mocked(fetchLogs).mockResolvedValue({
      events: [{
        id: 'event-1', timestamp: '2026-08-18T00:00:00Z', level: 'ERROR',
        service: 'checkout', environment: 'production', eventType: 'FAILURE',
        message: 'Payment failed', receivedAt: '2026-08-18T00:00:01Z',
        expireAt: '2026-08-25T00:00:01Z', organizationId: 'org-1',
        projectId: 'demo-project', apiKeyId: 'key-1',
      }],
      hasMore: false,
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><LogExplorerPage /></QueryClientProvider>)

    expect(await screen.findByText('Payment failed')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Level'), { target: { value: 'ERROR' } })
    fireEvent.change(screen.getByLabelText('Service'), { target: { value: 'checkout' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('Payment failed')).toBeInTheDocument()
    expect(fetchLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      projectId: 'demo-project', level: 'ERROR', service: 'checkout', limit: 100,
    }))

    fireEvent.click(screen.getByText('Payment failed'))
    expect(screen.getByLabelText('Event details')).toHaveTextContent('event-1')
  })
})
