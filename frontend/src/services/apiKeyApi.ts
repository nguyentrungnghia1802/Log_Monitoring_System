import type { ApiKeyMetadata, ApiKeyWithSecret } from '../types/apiKey'

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
      // Keep the HTTP fallback when the API has no JSON error body.
    }
    throw new Error(message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function fetchApiKeys(projectId: string): Promise<ApiKeyMetadata[]> {
  return request<ApiKeyMetadata[]>(`/projects/${encodeURIComponent(projectId)}/api-keys`)
}

export function createApiKey(projectId: string, name: string): Promise<ApiKeyWithSecret> {
  return request<ApiKeyWithSecret>(`/projects/${encodeURIComponent(projectId)}/api-keys`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function rotateApiKey(projectId: string, keyId: string): Promise<ApiKeyWithSecret> {
  return request<ApiKeyWithSecret>(
    `/projects/${encodeURIComponent(projectId)}/api-keys/${encodeURIComponent(keyId)}/rotate`,
    { method: 'POST' },
  )
}

export function revokeApiKey(projectId: string, keyId: string): Promise<void> {
  return request<void>(
    `/projects/${encodeURIComponent(projectId)}/api-keys/${encodeURIComponent(keyId)}`,
    { method: 'DELETE' },
  )
}
