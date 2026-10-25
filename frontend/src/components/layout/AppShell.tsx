import { Menubar } from 'primereact/menubar'
import { Button } from 'primereact/button'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { hasAdminRole } from '../../auth/keycloak'
import { AdSlot } from '../AdSlot'
import ErrorBoundary from '../ErrorBoundary'
import { useT } from '../../i18n'

export function AppShell() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const location = useLocation()
  const t = useT()

  const path = location.pathname
  const active = (prefixes: string[]) =>
    prefixes.some((p) => path === p || path.startsWith(`${p}/`)) ? 'nav-active' : ''

  const nav = (location.state as { nav?: string } | null)?.nav
  const fromGroups = nav === 'groups'
  const fromClaims = nav === 'claims'

  const items = [
    {
      label: t('nav.wishlists'),
      icon: 'pi pi-list',
      className: path === '/' ? 'nav-active' : active(fromGroups || fromClaims ? ['/wishlists'] : ['/wishlists', '/wishes']),
      command: () => navigate('/'),
    },
    { label: t('nav.groups'), icon: 'pi pi-users', className: active(fromGroups ? ['/groups', '/wishes'] : ['/groups']), command: () => navigate('/groups') },
    { label: t('nav.claims'), icon: 'pi pi-gift', className: active(fromClaims ? ['/claims', '/wishes'] : ['/claims']), command: () => navigate('/claims') },
    ...(hasAdminRole()
      ? [{ label: t('nav.admin'), icon: 'pi pi-shield', className: active(['/admin']), command: () => navigate('/admin') }]
      : []),
  ]

  const end = (
    <div className="shell-end">
      {user?.tier === 'PREMIUM' && <span className="premium-badge">PREMIUM</span>}
      <Button
        label={user?.displayname}
        icon="pi pi-user"
        text
        onClick={() => navigate('/profile')}
      />
      <Button
        icon="pi pi-sign-out"
        text
        severity="secondary"
        onClick={logout}
        aria-label={t('nav.logout')}
      />
    </div>
  )

  return (
    <div className="app-shell">
      <Menubar
        className="app-topbar"
        model={items}
        start={
          <Link to="/" className="app-brand">
            <i className="pi pi-gift" /> {t('app.name')}
          </Link>
        }
        end={end}
      />
      <AdSlot slot="top-banner" />
      <main className="app-main">
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
      <AdSlot slot="bottom-banner" />
    </div>
  )
}
