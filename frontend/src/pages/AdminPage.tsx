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
import { useT } from '../i18n'

/** App-admin console: aggregate numbers + tier management. No access to user content. */
export function AdminPage() {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [email, setEmail] = useState('')
  const [results, setResults] = useState<AdminUser[]>([])
  const [searched, setSearched] = useState(false)
  const { showError, showSuccess } = useApiToast()
  const t = useT()

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
      showSuccess(t('admin.tierChanged', { email: user.email, tier }))
    } catch (e) {
      showError(e)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>{t('admin.title')}</h1>
      </div>

      <div className="card-grid" style={{ marginBottom: '1.5rem' }}>
        <Card title={t('admin.users')}>
          <span className="stat-number">{stats?.user_count ?? '…'}</span>
        </Card>
        <Card title={t('admin.wishlists')}>
          <span className="stat-number">{stats?.wishlist_count ?? '…'}</span>
        </Card>
        <Card title={t('admin.wishes')}>
          <span className="stat-number">{stats?.wish_count ?? '…'}</span>
        </Card>
      </div>

      <Card title={t('admin.tierManagement')} subTitle={t('admin.tierHint')}>
        <div className="claim-row" style={{ marginBottom: '1rem' }}>
          <InputText
            placeholder={t('admin.emailPlaceholder')}
            value={email}
            style={{ flex: 1, maxWidth: 320 }}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && email.trim() && search()}
          />
          <Button
            label={t('common.search')}
            icon="pi pi-search"
            disabled={!email.trim()}
            onClick={search}
          />
        </div>
        {searched && (
          <DataTable value={results} dataKey="id" emptyMessage={t('admin.noAccounts')}>
            <Column field="email" header={t('common.email')} />
            <Column field="displayname" header={t('admin.displayname')} />
            <Column
              header={t('admin.tier')}
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
