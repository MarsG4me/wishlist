import { Menubar } from 'primereact/menubar'
import { Button } from 'primereact/button'
import { Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { AdSlot } from '../AdSlot'

export function AppShell() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const items = [
    { label: 'My wishlists', icon: 'pi pi-list', command: () => navigate('/') },
    { label: 'Groups', icon: 'pi pi-users', command: () => navigate('/groups') },
    { label: 'My claims', icon: 'pi pi-gift', command: () => navigate('/claims') },
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
      <Button icon="pi pi-sign-out" text severity="secondary" onClick={logout} aria-label="Logout" />
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
