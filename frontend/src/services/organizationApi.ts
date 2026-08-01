import type {
  InviteOrganizationMemberRequest,
  OrganizationMember,
  OrganizationSummary,
  UpdateOrganizationMemberRequest,
} from '../types/organization'

const API_BASE = '/api/v1'

function headers(includeJson = false): HeadersInit {
  const token = localStorage.getItem('accessToken')
  return {
    ...(includeJson ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...headers(Boolean(init?.body)),
      ...(init?.headers ?? {}),
    },
  })
  if (!response.ok) {
    let message = `Request failed: ${response.status}`
    try {
      const body = await response.json()
      message = body?.error?.message ?? body?.message ?? message
    } catch {
      // Keep the stable HTTP fallback when the server did not return JSON.
    }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function fetchCurrentOrganization(): Promise<OrganizationSummary> {
  return request<OrganizationSummary>('/organizations/current')
}

export function fetchOrganizationMembers(): Promise<OrganizationMember[]> {
  return request<OrganizationMember[]>('/organizations/current/users')
}

export function updateCurrentOrganization(name: string, settings: Record<string, string>) {
  return request<OrganizationSummary>('/organizations/current', {
    method: 'PATCH',
    body: JSON.stringify({ name, settings }),
  })
}

export function inviteOrganizationMember(requestBody: InviteOrganizationMemberRequest) {
  return request<OrganizationMember>('/organizations/current/users', {
    method: 'POST',
    body: JSON.stringify(requestBody),
  })
}

export function updateOrganizationMember(userId: string, requestBody: UpdateOrganizationMemberRequest) {
  return request<OrganizationMember>(`/organizations/current/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(requestBody),
  })
}

export function removeOrganizationMember(userId: string) {
  return request<void>(`/organizations/current/users/${userId}`, { method: 'DELETE' })
}
