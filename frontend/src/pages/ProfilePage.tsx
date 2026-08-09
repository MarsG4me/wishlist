import { useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { InputText } from 'primereact/inputtext'
import { Dropdown } from 'primereact/dropdown'
import { Tag } from 'primereact/tag'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { useAuth } from '../auth/AuthProvider'
import { deleteOwnAccount, updateDisplayname, updateEmail } from '../api/users'
import { openPasswordManagement, updateLocale } from '../api/keycloakAccount'
import { keycloak } from '../auth/keycloak'
import { currentLanguage } from '../i18n'
import { useApiToast } from '../hooks/useApiToast'

export function ProfilePage() {
  const { user, refreshUser, logout } = useAuth()
  const { showError, showSuccess } = useApiToast()
  const [displayname, setDisplayname] = useState(user?.displayname ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [language, setLanguage] = useState(currentLanguage())

  const saveDisplayname = async () => {
    try {
      await updateDisplayname(displayname)
      await refreshUser()
      showSuccess('Saved')
    } catch (e) {
      showError(e)
    }
  }

  /** Backend updates Keycloak first, then the DB; afterwards force a token refresh. */
  const saveEmail = async () => {
    try {
      await updateEmail(email)
      await keycloak.updateToken(-1).catch(() => keycloak.login())
      await refreshUser()
      showSuccess('Email updated')
    } catch (e) {
      showError(e)
    }
  }

  const saveLanguage = async (locale: string) => {
    setLanguage(locale)
    try {
      await updateLocale(locale)
      showSuccess('Language preference saved in your account')
    } catch (e) {
      showError(e)
    }
  }

  const removeAccount = () =>
    confirmDialog({
      message:
        'Delete your account? All your wishlists and groups become inaccessible. Logging in again within 30 days restores everything.',
      header: 'Delete account',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await deleteOwnAccount()
          logout()
        } catch (e) {
          showError(e)
        }
      },
    })

  return (
    <div>
      <ConfirmDialog />
      <div className="page-header">
        <h1>Profile</h1>
        {user?.tier === 'PREMIUM' && <Tag value="PREMIUM" />}
      </div>

      <Card title="Display name" subTitle="Only used inside this app">
        <div className="claim-row">
          <InputText value={displayname} maxLength={60} onChange={(e) => setDisplayname(e.target.value)} />
          <Button label="Save" icon="pi pi-check" disabled={!displayname.trim()} onClick={saveDisplayname} />
        </div>
      </Card>
      <br />

      <Card title="Account" subTitle="Email is your login name; changes go through Keycloak">
        <div className="form-field" style={{ maxWidth: 420 }}>
          <label htmlFor="profile-email">Email</label>
          <div className="claim-row">
            <InputText
              id="profile-email"
              value={email}
              style={{ flex: 1 }}
              onChange={(e) => setEmail(e.target.value)}
            />
            <Button label="Change" icon="pi pi-check" disabled={email === user?.email} onClick={saveEmail} />
          </div>
        </div>
        <div className="form-field" style={{ maxWidth: 420 }}>
          <label>Language (Keycloak account setting; the app itself follows your browser)</label>
          <Dropdown
            value={language}
            options={[
              { label: 'Deutsch', value: 'de' },
              { label: 'English', value: 'en' },
            ]}
            onChange={(e) => saveLanguage(e.value)}
          />
        </div>
        <Button label="Change password" icon="pi pi-key" outlined onClick={openPasswordManagement} />
      </Card>
      <br />

      <Card title="Danger zone">
        <Button label="Delete account" icon="pi pi-trash" severity="danger" outlined onClick={removeAccount} />
      </Card>
    </div>
  )
}
