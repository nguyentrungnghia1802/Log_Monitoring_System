import type {
  CreateProjectRequest,
  Project,
  UpdateProjectRequest,
  UpdateProjectRetentionRequest,
} from '../types/project'

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
      // Preserve a useful HTTP fallback when the API has no JSON body.
    }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function fetchProjects(): Promise<Project[]> {
  return request<Project[]>('/projects')
}

export function createProject(requestBody: CreateProjectRequest): Promise<Project> {
  return request<Project>('/projects', {
    method: 'POST',
    body: JSON.stringify(requestBody),
  })
}

export function updateProject(projectId: string, requestBody: UpdateProjectRequest): Promise<Project> {
  return request<Project>(`/projects/${projectId}`, {
    method: 'PATCH',
    body: JSON.stringify(requestBody),
  })
}

export function updateProjectRetention(projectId: string, requestBody: UpdateProjectRetentionRequest): Promise<Project> {
  return request<Project>(`/projects/${projectId}/retention`, {
    method: 'PUT',
    body: JSON.stringify(requestBody),
  })
}

export function deactivateProject(projectId: string): Promise<void> {
  return request<void>(`/projects/${projectId}`, { method: 'DELETE' })
}
