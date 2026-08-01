export type ProjectRetention = {
  defaultDays: number
  levelOverrides: Record<string, number>
}

export type ProjectIngestionSummary = {
  eventsLast24Hours: number
  errorEventsLast24Hours: number
  lastReceivedAt: string | null
}

export type Project = {
  id: string
  organizationId: string
  key: string
  name: string
  active: boolean
  environments: string[]
  retention: ProjectRetention
  settings: Record<string, string>
  services: string[]
  recentIngestion: ProjectIngestionSummary
  createdAt: string
  updatedAt: string
}

export type CreateProjectRequest = {
  key: string
  name: string
  environments: string[]
  retention: ProjectRetention
  settings?: Record<string, string>
}

export type UpdateProjectRequest = {
  name: string
  environments: string[]
  settings?: Record<string, string>
}

export type UpdateProjectRetentionRequest = ProjectRetention
