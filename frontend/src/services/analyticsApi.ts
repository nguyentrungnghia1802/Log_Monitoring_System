import type { AnalyticsSearchParams, AnalyticsSummaryResponse, AnalyticsHistogramResponse } from '../types/analytics'

const API_BASE = '/api/v1'

export async function fetchAnalyticsSummary(params: AnalyticsSearchParams): Promise<AnalyticsSummaryResponse> {
    const query = new URLSearchParams()
    if (params.startTime) query.set('startTime', params.startTime)
    if (params.endTime) query.set('endTime', params.endTime)
    if (params.environment) query.set('environment', params.environment)
    if (params.service) query.set('service', params.service)

    const res = await fetch(`${API_BASE}/projects/${params.projectId}/analytics/summary?${query.toString()}`)
    if (!res.ok) {
        throw new Error(`Failed to fetch analytics summary: ${res.statusText}`)
    }
    return res.json()
}

export async function fetchAnalyticsHistogram(params: AnalyticsSearchParams): Promise<AnalyticsHistogramResponse> {
    const query = new URLSearchParams()
    if (params.startTime) query.set('startTime', params.startTime)
    if (params.endTime) query.set('endTime', params.endTime)
    if (params.interval) query.set('interval', params.interval)
    if (params.environment) query.set('environment', params.environment)
    if (params.service) query.set('service', params.service)

    const res = await fetch(`${API_BASE}/projects/${params.projectId}/analytics/histogram?${query.toString()}`)
    if (!res.ok) {
        throw new Error(`Failed to fetch analytics histogram: ${res.statusText}`)
    }
    return res.json()
}
