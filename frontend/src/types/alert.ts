export interface AlertRule {
    id?: string
    name: string
    projectId: string
    enabled: boolean
    environment?: string
    service?: string
    levels?: string[]
    eventTypes?: string[]
    windowSeconds: number
    threshold: number
    cooldownSeconds: number
    cooldownUntil?: string
    createdAt?: string
    updatedAt?: string
}

export interface AlertOccurrence {
    id: string
    ruleId: string
    ruleName: string
    projectId: string
    triggeredAt: string
    windowStart: string
    windowEnd: string
    observedValue: number
    threshold: number
    status: 'TRIGGERED' | 'ACKNOWLEDGED'
    deliveryStatus: 'PENDING' | 'DELIVERED' | 'FAILED'
    attemptCount: number
    lastAttemptAt?: string
    lastError?: string
}
