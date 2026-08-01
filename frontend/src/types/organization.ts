export type OrganizationRole = 'ORGANIZATION_ADMIN' | 'PROJECT_OPERATOR' | 'VIEWER'

export interface OrganizationSummary {
  id: string
  slug: string
  name: string
  active: boolean
  settings: Record<string, string>
  memberCount: number
  createdAt?: string
  updatedAt?: string
}

export interface OrganizationMember {
  id: string
  username: string
  email: string
  role: OrganizationRole | null
  active: boolean
  createdAt?: string
  updatedAt?: string
}

export interface InviteOrganizationMemberRequest {
  username: string
  email: string
  password: string
  role: OrganizationRole
}

export interface UpdateOrganizationMemberRequest {
  role?: OrganizationRole
  active?: boolean
}
