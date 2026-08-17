import type {
  CreateProjectRequest,
  Project,
  UpdateProjectRequest,
  UpdateProjectRetentionRequest,
} from '../types/project'
import { apiRequest } from './http'

export function fetchProjects(): Promise<Project[]> {
  return apiRequest<Project[]>('/projects')
}

export function createProject(requestBody: CreateProjectRequest): Promise<Project> {
  return apiRequest<Project>('/projects', {
    method: 'POST',
    body: JSON.stringify(requestBody),
  })
}

export function updateProject(projectId: string, requestBody: UpdateProjectRequest): Promise<Project> {
  return apiRequest<Project>(`/projects/${projectId}`, {
    method: 'PATCH',
    body: JSON.stringify(requestBody),
  })
}

export function updateProjectRetention(projectId: string, requestBody: UpdateProjectRetentionRequest): Promise<Project> {
  return apiRequest<Project>(`/projects/${projectId}/retention`, {
    method: 'PUT',
    body: JSON.stringify(requestBody),
  })
}

export function deactivateProject(projectId: string): Promise<void> {
  return apiRequest<void>(`/projects/${projectId}`, { method: 'DELETE' })
}
