import { useEffect, useState } from 'react'
import { Card } from 'primereact/card'
import { InputText } from 'primereact/inputtext'
import { Button } from 'primereact/button'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Dropdown } from 'primereact/dropdown'
import { Tag } from 'primereact/tag'
import { Checkbox } from 'primereact/checkbox'
import { Dialog } from 'primereact/dialog'
import { getAdminStats, type AdminStats, type AdminUser } from '../api/admin'
import {
  deleteAdminUser,
  downloadUserExport,
  searchAdminUsers,
  type AdminUserDeletable,
} from '../api/adminDsgvo'
import { AdminLinkReports } from './AdminLinkReports'
import { PageHeader } from '../components/PageStates'
import { setTier } from '../api/users'
import type { UserTier } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'
import { useT } from '../i18n'

/** App-admin console: aggregate numbers + tier management. No access to user content. */
export function AdminPage() {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [email, setEmail] = useState('')
  const [results, setResults] = useState<AdminUserDeletable[]>([])
  const [searched, setSearched] = useState(false)
  const [includeDeleted, setIncludeDeleted] = useState(false)
  const [target, setTarget] = useState<AdminUserDeletable | null>(null)
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  useEffect(() => {
    getAdminStats().then(setStats).catch(showError)
  }, [])

  const search = async (withDeleted = includeDeleted) => {
    try {
      const page = await searchAdminUsers(email.trim(), withDeleted, { limit: 100 })
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

  const exportData = async (user: AdminUser) => {
    try {
      await downloadUserExport(user.id)
      showSuccess(t('admin.exported'))
    } catch (e) {
      showError(e)
    }
  }

  const removeUser = async (immediate: boolean) => {
    if (!target) return
    try {
      await deleteAdminUser(target.id, immediate)
      showSuccess(t(immediate ? 'admin.erased' : 'admin.deleted', { email: target.email }))
      setTarget(null)
      await search()
    } catch (e) {
      showError(e)
    }
  }

  return (
    <div>
      <PageHeader title={t('admin.title')} />

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
            onClick={() => search()}
          />
          <label className="inline-check">
            <Checkbox
              checked={includeDeleted}
              onChange={(e) => {
                setIncludeDeleted(!!e.checked)
                if (searched && email.trim()) search(!!e.checked)
              }}
            />
            {t('admin.includeDeleted')}
          </label>
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
            <Column
              header={t('admin.deletedAt')}
              body={(user: AdminUserDeletable) =>
                user.deleted_at ? (
                  <Tag value={new Date(user.deleted_at).toLocaleDateString()} severity="danger" />
                ) : (
                  <span className="muted">{t('common.none')}</span>
                )
              }
            />
            <Column
              header=""
              body={(user: AdminUserDeletable) => (
                <div className="claim-row">
                  <Button
                    label={t('admin.export')}
                    icon="pi pi-download"
                    text
                    onClick={() => exportData(user)}
                  />
                  <Button
                    label={t('admin.deleteAccount')}
                    icon="pi pi-trash"
                    text
                    severity="danger"
                    onClick={() => setTarget(user)}
                  />
                </div>
              )}
            />
          </DataTable>
        )}
      </Card>
      <AdminLinkReports />
      <Dialog
        header={t('admin.deleteHeader')}
        visible={target !== null}
        style={{ width: 'min(480px, 95vw)' }}
        onHide={() => setTarget(null)}
      >
        <p>{t('admin.deleteMessage', { email: target?.email ?? '' })}</p>
        <p className="danger-note">
          <i className="pi pi-exclamation-triangle" /> {t('admin.eraseWarning')}
        </p>
        <div className="claim-row">
          {!target?.deleted_at && (
            <Button
              label={t('admin.deleteAccount')}
              icon="pi pi-trash"
              severity="danger"
              onClick={() => removeUser(false)}
            />
          )}
          <Button
            label={t('admin.eraseNow')}
            icon="pi pi-bolt"
            severity="danger"
            outlined
            onClick={() => removeUser(true)}
          />
        </div>
      </Dialog>
    </div>
  )
}
