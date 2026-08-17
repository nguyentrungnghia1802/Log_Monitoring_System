import type { ApiKeyMetadata, ApiKeyWithSecret } from '../types/apiKey'
import { apiRequest } from './http'

export function fetchApiKeys(projectId: string): Promise<ApiKeyMetadata[]> {
  return apiRequest<ApiKeyMetadata[]>(`/projects/${encodeURIComponent(projectId)}/api-keys`)
}

export function createApiKey(projectId: string, name: string): Promise<ApiKeyWithSecret> {
  return apiRequest<ApiKeyWithSecret>(`/projects/${encodeURIComponent(projectId)}/api-keys`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function rotateApiKey(projectId: string, keyId: string): Promise<ApiKeyWithSecret> {
  return apiRequest<ApiKeyWithSecret>(
    `/projects/${encodeURIComponent(projectId)}/api-keys/${encodeURIComponent(keyId)}/rotate`,
    { method: 'POST' },
  )
}

export function revokeApiKey(projectId: string, keyId: string): Promise<void> {
  return apiRequest<void>(
    `/projects/${encodeURIComponent(projectId)}/api-keys/${encodeURIComponent(keyId)}`,
    { method: 'DELETE' },
  )
}
