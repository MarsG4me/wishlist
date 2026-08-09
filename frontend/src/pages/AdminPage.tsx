import { useEffect, useState } from 'react'
import { Card } from 'primereact/card'
import { InputText } from 'primereact/inputtext'
import { Button } from 'primereact/button'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Dropdown } from 'primereact/dropdown'
import { getAdminStats, searchUsersByEmail, type AdminStats, type AdminUser } from '../api/admin'
import { setTier } from '../api/users'
import type { UserTier } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'

/** App-admin console: aggregate numbers + tier management. No access to user content. */
export function AdminPage() {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [email, setEmail] = useState('')
  const [results, setResults] = useState<AdminUser[]>([])
  const [searched, setSearched] = useState(false)
  const { showError, showSuccess } = useApiToast()

  useEffect(() => {
    getAdminStats().then(setStats).catch(showError)
  }, [])

  const search = async () => {
    try {
      const page = await searchUsersByEmail(email.trim(), { limit: 100 })
      setResults(page.items)
      setSearched(true)
    } catch (e) {
      showError(e)
    }
  }

  const changeTier = async (user: AdminUser, tier: UserTier) => {
    try {
      await setTier(user.id, tier)
      setResults(results.map((r) => (r.id === user.id ? { ...r, tier } : r)))
      showSuccess(`${user.email} is now ${tier}`)
    } catch (e) {
      showError(e)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Admin</h1>
      </div>

      <div className="card-grid" style={{ marginBottom: '1.5rem' }}>
        <Card title="Users">
          <span className="stat-number">{stats?.user_count ?? '…'}</span>
        </Card>
        <Card title="Wishlists">
          <span className="stat-number">{stats?.wishlist_count ?? '…'}</span>
        </Card>
        <Card title="Wishes">
          <span className="stat-number">{stats?.wish_count ?? '…'}</span>
        </Card>
      </div>

      <Card title="Tier management" subTitle="Search by email only; account data, no content access">
        <div className="claim-row" style={{ marginBottom: '1rem' }}>
          <InputText
            placeholder="email…"
            value={email}
            style={{ flex: 1, maxWidth: 320 }}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && email.trim() && search()}
          />
          <Button label="Search" icon="pi pi-search" disabled={!email.trim()} onClick={search} />
        </div>
        {searched && (
          <DataTable value={results} dataKey="id" emptyMessage="No accounts match this email.">
            <Column field="email" header="Email" />
            <Column field="displayname" header="Displayname" />
            <Column
              header="Tier"
              body={(user: AdminUser) => (
                <Dropdown
                  value={user.tier}
                  options={[
                    { label: 'FREE', value: 'FREE' },
                    { label: 'PREMIUM', value: 'PREMIUM' },
                  ]}
                  onChange={(e) => changeTier(user, e.value)}
                />
              )}
            />
          </DataTable>
        )}
      </Card>
    </div>
  )
}
