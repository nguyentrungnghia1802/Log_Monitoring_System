import type { LogSearchParams, LogSearchResponse, LogEvent } from '../types/log'

const API_BASE = '/api/v1'

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

    const res = await fetch(`${API_BASE}/projects/${params.projectId}/logs?${query.toString()}`)
    if (!res.ok) {
        throw new Error(`Failed to fetch logs: ${res.statusText}`)
    }
    return res.json()
}

export async function fetchLogById(projectId: string, id: string): Promise<LogEvent> {
    const res = await fetch(`${API_BASE}/projects/${projectId}/logs/${id}`)
    if (!res.ok) {
        throw new Error(`Failed to fetch log ${id}: ${res.statusText}`)
    }
    return res.json()
}

export async function fetchAlertRules(projectId: string) {
    const res = await fetch(`${API_BASE}/projects/${projectId}/alert-rules`)
    if (!res.ok) throw new Error(`Failed to fetch alert rules: ${res.statusText}`)
    return res.json()
}

export async function createAlertRule(projectId: string, rule: any) {
    const res = await fetch(`${API_BASE}/projects/${projectId}/alert-rules`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(rule)
    })
    if (!res.ok) throw new Error(`Failed to create alert rule: ${res.statusText}`)
    return res.json()
}

export async function toggleAlertRule(projectId: string, ruleId: string, enable: boolean) {
    const action = enable ? 'enable' : 'disable'
    const res = await fetch(`${API_BASE}/projects/${projectId}/alert-rules/${ruleId}/${action}`, { method: 'POST' })
    if (!res.ok) throw new Error(`Failed to toggle alert rule: ${res.statusText}`)
    return res.json()
}

export async function deleteAlertRule(projectId: string, ruleId: string) {
    const res = await fetch(`${API_BASE}/projects/${projectId}/alert-rules/${ruleId}`, { method: 'DELETE' })
    if (!res.ok) throw new Error(`Failed to delete alert rule: ${res.statusText}`)
}

export async function fetchAlerts(projectId: string) {
    const res = await fetch(`${API_BASE}/projects/${projectId}/alerts`)
    if (!res.ok) throw new Error(`Failed to fetch alerts: ${res.statusText}`)
    return res.json()
}

export async function acknowledgeAlert(projectId: string, alertId: string) {
    const res = await fetch(`${API_BASE}/projects/${projectId}/alerts/${alertId}/acknowledge`, { method: 'POST' })
    if (!res.ok) throw new Error(`Failed to acknowledge alert: ${res.statusText}`)
    return res.json()
}

export async function retryAlertNotification(projectId: string, alertId: string) {
    const res = await fetch(`${API_BASE}/projects/${projectId}/alerts/${alertId}/retry-notification`, { method: 'POST' })
    if (!res.ok) throw new Error(`Failed to retry notification: ${res.statusText}`)
    return res.json()
}

