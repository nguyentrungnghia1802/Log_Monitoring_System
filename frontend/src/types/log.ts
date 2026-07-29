export interface LogEvent {
    id: string
    eventId?: string
    timestamp: string
    level: string
    service: string
    environment: string
    eventType: string
    message: string
    traceId?: string
    requestId?: string
    exception?: Record<string, unknown>
    context?: Record<string, unknown>
    tags?: Record<string, unknown>
    receivedAt: string
    expireAt: string
    organizationId: string
    projectId: string
    apiKeyId: string
    errorFingerprint?: string
}

export interface LogSearchResponse {
    events: LogEvent[]
    nextCursor?: string
    hasMore: boolean
}

export interface LogSearchParams {
    projectId: string
    startTime?: string
    endTime?: string
    level?: string
    service?: string
    environment?: string
    eventType?: string
    traceId?: string
    requestId?: string
    errorFingerprint?: string
    search?: string
    cursor?: string
    limit?: number
}
