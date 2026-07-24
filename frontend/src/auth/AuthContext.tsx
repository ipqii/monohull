import { createContext, useContext, useEffect, useState, ReactNode } from 'react'
import {
  AuthUser,
  getMe,
  login as apiLogin,
  logout as apiLogout,
  setUnauthorizedHandler,
} from '../api/client'

interface AuthState {
  user: AuthUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // A 401 from any non-auth API call means the session expired.
    setUnauthorizedHandler(() => setUser(null))
    getMe()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
    return () => setUnauthorizedHandler(null)
  }, [])

  const login = async (username: string, password: string) => {
    await apiLogin(username, password)
    setUser(await getMe())
  }

  const logout = async () => {
    try {
      await apiLogout()
    } finally {
      setUser(null)
    }
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
