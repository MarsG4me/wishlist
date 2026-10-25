import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { keycloak } from './keycloak'
import { getOwnUser } from '../api/users'
import type { OwnUser } from '../api/types'
import { ApiError } from '../api/errors'
import { setLanguage, tError, useT } from '../i18n'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'

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
  const [loadError, setLoadError] = useState<string | null>(null)
  const t = useT()
  const initStarted = useRef(false)

  useEffect(() => {
    if (initStarted.current) return
    initStarted.current = true
    // the invite landing page is public: look for a session but do not force a login
    const publicRoute = window.location.pathname.startsWith('/join/')
    keycloak
      .init({
        onLoad: publicRoute ? 'check-sso' : 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then(async (authenticated) => {
        if (authenticated) await loadUserSafe()
        if (authenticated || publicRoute) setReady(true)
      })
      .catch((err) => console.error('Keycloak init failed', err))
  }, [])

  /** The account locale drives the UI language, so it is applied with every load. */
  const loadUser = async () => {
    const loaded = await getOwnUser()
    setLanguage(loaded.locale)
    setUser(loaded)
  }

  /** Loads the user for the startup/retry paths; a failure is kept for the error card. */
  const loadUserSafe = async () => {
    try {
      await loadUser()
      setLoadError(null)
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.errorCode : 'INTERNAL_ERROR')
    }
  }

  const refreshUser = loadUser
  const logout = () => keycloak.logout({ redirectUri: window.location.origin })

  if (loadError) {
    const deleted = loadError === 'ACCOUNT_DELETED'
    return (
      <main className="app-loading" style={{ fontSize: 'inherit' }}>
        <Card title={deleted ? t('auth.deletedTitle') : t('auth.loadFailedTitle')} style={{ maxWidth: 480 }}>
          <p>{tError(loadError)}</p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
            {deleted ? (
              <Button label={t('auth.restore')} icon="pi pi-replay" onClick={() => keycloak.login({ prompt: 'login' })} />
            ) : (
              <Button label={t('auth.retry')} icon="pi pi-refresh" onClick={loadUserSafe} />
            )}
            <Button label={t('nav.logout')} icon="pi pi-sign-out" outlined onClick={logout} />
          </div>
        </Card>
      </main>
    )
  }
  if (!ready) return <div className="app-loading">…</div>
  return <AuthContext.Provider value={{ user, refreshUser, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
