import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fetchPlatformHealth } from '../../services/systemApi'
import { PlatformHealthPage } from './PlatformHealthPage'

vi.mock('../../services/systemApi', () => ({
  fetchPlatformHealth: vi.fn(),
}))

const snapshot = {
  generatedAt: '2026-08-18T06:00:00Z',
  readiness: { status: 'UP', dependencies: { readinessState: 'UP', mongo: 'UP' } },
  ingestion: {
    receivedTotal: 100, acceptedTotal: 96, validationRejectedTotal: 2,
    backpressureRejectedTotal: 2, shutdownRejectedTotal: 0, queueDepth: 12,
    queueCapacity: 100, queueUtilizationPercent: 12, activeWorkers: 2,
    workerCapacity: 4, averageBatchSize: 8, maxBatchSize: 20,
  },
  persistence: {
    eventsSavedTotal: 96, eventsFailedTotal: 1, retriesTotal: 2,
    failuresTotal: 1, averageDurationMs: 12.4, maxDurationMs: 38.2,
  },
  liveTail: {
    activeSessions: 3, activeSubscriptions: 4, eventsSentTotal: 50,
    eventsDroppedTotal: 1, authorizationFailuresTotal: 2,
  },
  alerts: {
    evaluationsTotal: 20, triggeredTotal: 2, deliverySuccessTotal: 1,
    deliveryFailureTotal: 1, deliveryRetryTotal: 1,
  },
  runtime: {
    heapUsedBytes: 1024 * 1024 * 128, heapMaxBytes: 1024 * 1024 * 512,
    gcPauseCount: 4, liveThreads: 18, processCpuUsage: 0.12, systemCpuUsage: 0.2,
  },
}

describe('PlatformHealthPage', () => {
  it('shows operator telemetry panels from the platform snapshot', async () => {
    vi.mocked(fetchPlatformHealth).mockResolvedValue(snapshot)

    render(<PlatformHealthPage />)

    expect(await screen.findByRole('heading', { name: 'Platform Health' })).toBeInTheDocument()
    expect(screen.getByText('Readiness and dependencies')).toBeInTheDocument()
    expect(screen.getByText('Accepted rate')).toBeInTheDocument()
    expect(screen.getByText('96 total events')).toBeInTheDocument()
    expect(screen.getByText('12 / 100')).toBeInTheDocument()
    expect(screen.getByText('12.4 ms')).toBeInTheDocument()
    expect(screen.getByText('Alert delivery failures')).toBeInTheDocument()
  })
})
