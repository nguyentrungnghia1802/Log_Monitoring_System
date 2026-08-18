import type { PlatformHealthSnapshot } from '../types/system'
import { apiRequest } from './http'

export function fetchPlatformHealth(): Promise<PlatformHealthSnapshot> {
  return apiRequest<PlatformHealthSnapshot>('/system/health-dashboard')
}
