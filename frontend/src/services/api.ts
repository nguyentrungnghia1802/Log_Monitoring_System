import type { LogSearchParams, LogSearchResponse, LogEvent } from '../types/log'
import type { AlertOccurrence, AlertRule } from '../types/alert'
import { apiRequest } from './http'

export async function fetchLogs(params: LogSearchParams): Promise<LogSearchResponse> {
    const query = new URLSearchParams()
    if (params.startTime) query.set('startTime', params.startTime)
    if (params.endTime) query.set('endTime', params.endTime)
    if (params.level) query.set('level', params.level)
    if (params.service) query.set('service', params.service)
    if (params.environment) query.set('environment', params.environment)
    if (params.eventType) query.set('eventType', params.eventType)
    if (params.traceId) query.set('traceId', params.traceId)
    if (params.requestId) query.set('requestId', params.requestId)
    if (params.errorFingerprint) query.set('errorFingerprint', params.errorFingerprint)
    if (params.search) query.set('search', params.search)
    if (params.cursor) query.set('cursor', params.cursor)
    if (params.limit) query.set('limit', params.limit.toString())

    return apiRequest<LogSearchResponse>(`/projects/${params.projectId}/logs?${query.toString()}`)
}

export async function fetchLogById(projectId: string, id: string): Promise<LogEvent> {
    return apiRequest<LogEvent>(`/projects/${projectId}/logs/${id}`)
}

export async function fetchAlertRules(projectId: string): Promise<AlertRule[]> {
    return apiRequest<AlertRule[]>(`/projects/${projectId}/alert-rules`)
}

export async function createAlertRule(projectId: string, rule: Partial<AlertRule>): Promise<AlertRule> {
    return apiRequest<AlertRule>(`/projects/${projectId}/alert-rules`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(rule)
    })
}

export async function toggleAlertRule(projectId: string, ruleId: string, enable: boolean): Promise<AlertRule> {
    const action = enable ? 'enable' : 'disable'
    return apiRequest<AlertRule>(`/projects/${projectId}/alert-rules/${ruleId}/${action}`, { method: 'POST' })
}

export async function deleteAlertRule(projectId: string, ruleId: string) {
    return apiRequest<void>(`/projects/${projectId}/alert-rules/${ruleId}`, { method: 'DELETE' })
}

export async function fetchAlerts(projectId: string): Promise<AlertOccurrence[]> {
    return apiRequest<AlertOccurrence[]>(`/projects/${projectId}/alerts`)
}

export async function fetchAlertById(projectId: string, alertId: string): Promise<AlertOccurrence> {
    return apiRequest<AlertOccurrence>(`/projects/${projectId}/alerts/${alertId}`)
}

export async function acknowledgeAlert(projectId: string, alertId: string): Promise<AlertOccurrence> {
    return apiRequest<AlertOccurrence>(`/projects/${projectId}/alerts/${alertId}/acknowledge`, { method: 'POST' })
}

export async function retryAlertNotification(projectId: string, alertId: string): Promise<AlertOccurrence> {
    return apiRequest<AlertOccurrence>(`/projects/${projectId}/alerts/${alertId}/retry-notification`, { method: 'POST' })
}

