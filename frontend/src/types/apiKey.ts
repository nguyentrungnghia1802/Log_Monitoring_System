export type ApiKeyStatus = 'ACTIVE' | 'REVOKED' | string

export type ApiKeyMetadata = {
  id: string
  projectId: string
  name: string
  publicId: string
  secretLast4: string | null
  status: ApiKeyStatus
  createdAt: string
  lastUsedAt: string | null
  revokedAt: string | null
}

export type ApiKeyWithSecret = ApiKeyMetadata & {
  rawApiKey: string
}
