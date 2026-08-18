export interface ReadinessSnapshot {
  status: string
  dependencies: Record<string, string>
}

export interface IngestionSnapshot {
  receivedTotal: number
  acceptedTotal: number
  validationRejectedTotal: number
  backpressureRejectedTotal: number
  shutdownRejectedTotal: number
  queueDepth: number
  queueCapacity: number
  queueUtilizationPercent: number
  activeWorkers: number
  workerCapacity: number
  averageBatchSize: number
  maxBatchSize: number
}

export interface PersistenceSnapshot {
  eventsSavedTotal: number
  eventsFailedTotal: number
  retriesTotal: number
  failuresTotal: number
  averageDurationMs: number
  maxDurationMs: number
}

export interface LiveTailSnapshot {
  activeSessions: number
  activeSubscriptions: number
  eventsSentTotal: number
  eventsDroppedTotal: number
  authorizationFailuresTotal: number
}

export interface AlertSnapshot {
  evaluationsTotal: number
  triggeredTotal: number
  deliverySuccessTotal: number
  deliveryFailureTotal: number
  deliveryRetryTotal: number
}

export interface RuntimeSnapshot {
  heapUsedBytes: number
  heapMaxBytes: number
  gcPauseCount: number
  liveThreads: number
  processCpuUsage: number
  systemCpuUsage: number
}

export interface PlatformHealthSnapshot {
  generatedAt: string
  readiness: ReadinessSnapshot
  ingestion: IngestionSnapshot
  persistence: PersistenceSnapshot
  liveTail: LiveTailSnapshot
  alerts: AlertSnapshot
  runtime: RuntimeSnapshot
}
