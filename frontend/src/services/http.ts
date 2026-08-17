import { getAccessToken } from '../auth/authToken'

const API_BASE = '/api/v1'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(
    status: number,
    code: string,
    message: string,
  ) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, authenticated = true): Promise<T> {
  const token = authenticated ? getAccessToken() : null
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers ?? {}),
    },
  })

  if (!response.ok) {
    let code = `HTTP_${response.status}`
    let message = `Request failed: ${response.status}`
    try {
      const body = await response.json()
      code = body?.error?.code ?? code
      message = body?.error?.message ?? body?.message ?? message
    } catch {
      // Keep the stable HTTP fallback when the response has no JSON body.
    }
    throw new ApiError(response.status, code, message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
