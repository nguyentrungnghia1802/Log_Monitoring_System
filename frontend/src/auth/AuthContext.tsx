import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { login as loginRequest, logout as logoutRequest, refreshSession, type AuthenticatedUser, type AuthResponse } from '../services/authApi'
import { setAccessToken } from './authToken'
import { AuthContext } from './authContextValue'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [ready, setReady] = useState(false)
  const refreshTimer = useRef<number | null>(null)

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current)
    refreshTimer.current = null
  }, [])

  const acceptSession = useCallback((session: AuthResponse) => {
    setAccessToken(session.accessToken)
    setUser(session.user)
    if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current)
    const refreshInMs = Math.max(10_000, (session.expiresInSeconds - 60) * 1_000)
    refreshTimer.current = window.setTimeout(() => {
      void refreshSession().then(acceptSession).catch(clearSession)
    }, refreshInMs)
  }, [clearSession])

  useEffect(() => {
    let active = true
    void refreshSession()
      .then((session) => { if (active) acceptSession(session) })
      .catch(() => { if (active) clearSession() })
      .finally(() => { if (active) setReady(true) })
    return () => {
      active = false
      if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current)
    }
  }, [acceptSession, clearSession])

  const login = useCallback(async (email: string, password: string) => {
    acceptSession(await loginRequest(email, password))
  }, [acceptSession])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      clearSession()
    }
  }, [clearSession])

  const value = useMemo(() => ({ user, ready, login, logout }), [user, ready, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
