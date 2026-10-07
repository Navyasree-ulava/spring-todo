import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler } from '@/lib/api'
import type { User } from '@/lib/types'

interface AuthState {
  user: User | null
  googleEnabled: boolean
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [googleEnabled, setGoogleEnabled] = useState(false)
  const [loading, setLoading] = useState(true)

  // On mount: ask the backend who we are. The session lives in an httpOnly
  // cookie, so there is nothing for us to read on the client - /me is the only
  // way to know whether we are signed in after a refresh.
  useEffect(() => {
    let cancelled = false

    async function bootstrap() {
      // These must NOT be awaited together in one Promise.all.
      // /auth/me returns 401 when signed out, which rejects the whole batch and
      // discards the providers result - leaving googleEnabled false forever, so
      // the Google button only ever appeared for users who were already signed in
      // and never on the login page where it actually matters.
      const providersRequest = api
        .get<{ google: boolean }>('/auth/providers')
        .then((response) => response.google)
        .catch(() => false)

      let currentUser: User | null = null
      try {
        currentUser = await api.get<User>('/auth/me')
      } catch {
        // 401 simply means signed out, which is a normal state - not an error.
      }

      const google = await providersRequest
      if (!cancelled) {
        setUser(currentUser)
        setGoogleEnabled(google)
      }
      if (!cancelled) {
        setLoading(false)
      }
    }

    bootstrap()
    return () => {
      cancelled = true
    }
  }, [])

  // Any 401 anywhere in the app means the session died (expired, revoked,
  // logged out in another tab) - drop straight back to signed out.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      googleEnabled,
      loading,
      login: async (email, password) => {
        setUser(await api.post<User>('/auth/login', { email, password }))
      },
      register: async (email, password, displayName) => {
        setUser(await api.post<User>('/auth/register', { email, password, displayName }))
      },
      logout: async () => {
        try {
          await api.post<void>('/auth/logout')
        } finally {
          // Clear locally even if the request failed, so the UI can never get
          // stuck showing a stale user.
          setUser(null)
        }
      },
      refresh: async () => {
        setUser(await api.get<User>('/auth/me'))
      },
    }),
    [user, googleEnabled, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
