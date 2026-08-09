import { Menubar } from 'primereact/menubar'
import { Button } from 'primereact/button'
import { Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { hasAdminRole } from '../../auth/keycloak'
import { AdSlot } from '../AdSlot'
import { useT } from '../../i18n'

export function AppShell() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const t = useT()

  const items = [
    { label: t('nav.wishlists'), icon: 'pi pi-list', command: () => navigate('/') },
    { label: t('nav.groups'), icon: 'pi pi-users', command: () => navigate('/groups') },
    { label: t('nav.claims'), icon: 'pi pi-gift', command: () => navigate('/claims') },
    ...(hasAdminRole()
      ? [{ label: t('nav.admin'), icon: 'pi pi-shield', command: () => navigate('/admin') }]
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
      <Menubar model={items} end={end} />
      <AdSlot slot="top-banner" />
      <main className="app-main">
        <Outlet />
      </main>
      <AdSlot slot="bottom-banner" />
    </div>
  )
}
