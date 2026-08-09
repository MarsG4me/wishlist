import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { keycloak } from './keycloak'
import { getOwnUser } from '../api/users'
import type { OwnUser } from '../api/types'
import { setLanguage } from '../i18n'

interface AuthContextValue {
  user: OwnUser | null
  refreshUser: () => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/** Initializes Keycloak (redirect login) and loads the JIT-provisioned own user. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)
  const [user, setUser] = useState<OwnUser | null>(null)
  const initStarted = useRef(false)

  useEffect(() => {
    if (initStarted.current) return
    initStarted.current = true
    keycloak
      .init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
      .then(async (authenticated) => {
        if (authenticated) {
          await loadUser()
          setReady(true)
        }
      })
      .catch((err) => console.error('Keycloak init failed', err))
  }, [])

  /** The account locale drives the UI language, so it is applied with every load. */
  const loadUser = async () => {
    const loaded = await getOwnUser()
    setLanguage(loaded.locale)
    setUser(loaded)
  }

  const refreshUser = loadUser
  const logout = () => keycloak.logout({ redirectUri: window.location.origin })

  if (!ready) return <div className="app-loading">…</div>
  return <AuthContext.Provider value={{ user, refreshUser, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
