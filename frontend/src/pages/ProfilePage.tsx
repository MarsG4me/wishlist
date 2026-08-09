import { useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { InputText } from 'primereact/inputtext'
import { Dropdown } from 'primereact/dropdown'
import { Tag } from 'primereact/tag'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { useAuth } from '../auth/AuthProvider'
import { deleteOwnAccount, updateDisplayname, updateEmail, updateLocale, updatePassword } from '../api/users'
import { Password } from 'primereact/password'
import { keycloak } from '../auth/keycloak'
import { setLanguage, useT } from '../i18n'
import type { Locale } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'

export function ProfilePage() {
  const { user, refreshUser, logout } = useAuth()
  const { showError, showSuccess } = useApiToast()
  const t = useT()
  const [displayname, setDisplayname] = useState(user?.displayname ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [locale, setLocale] = useState<Locale>(user?.locale ?? 'EN')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeatPassword, setRepeatPassword] = useState('')

  const saveDisplayname = async () => {
    try {
      await updateDisplayname(displayname)
      await refreshUser()
      showSuccess(t('common.saved'))
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
      showSuccess(t('profile.emailUpdated'))
    } catch (e) {
      showError(e)
    }
  }

  const savePassword = async () => {
    try {
      await updatePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setRepeatPassword('')
      showSuccess(t('profile.passwordChanged'))
    } catch (e) {
      showError(e)
    }
  }

  /** The UI only switches once the backend stored the change; on failure the dropdown stays put. */
  const saveLocale = async (next: Locale) => {
    const previous = locale
    setLocale(next)
    try {
      const updated = await updateLocale(next)
      setLanguage(updated.locale)
      await refreshUser()
      // t() is re-read here, so the confirmation already speaks the new language
      showSuccess(t('profile.languageSaved'))
    } catch (e) {
      setLocale(previous)
      showError(e)
    }
  }

  const removeAccount = () =>
    confirmDialog({
      message: t('profile.deleteAccountMessage'),
      header: t('profile.deleteAccount'),
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
        <h1>{t('profile.title')}</h1>
        {user?.tier === 'PREMIUM' && <Tag value="PREMIUM" />}
      </div>

      <Card title={t('profile.displayName')} subTitle={t('profile.displayNameHint')}>
        <div className="claim-row">
          <InputText value={displayname} maxLength={60} onChange={(e) => setDisplayname(e.target.value)} />
          <Button
            label={t('common.save')}
            icon="pi pi-check"
            disabled={!displayname.trim()}
            onClick={saveDisplayname}
          />
        </div>
      </Card>
      <br />

      <Card title={t('profile.language')} subTitle={t('profile.languageHint')}>
        <div className="form-field" style={{ maxWidth: 420 }}>
          <label htmlFor="profile-language">{t('profile.interfaceLanguage')}</label>
          <Dropdown
            inputId="profile-language"
            value={locale}
            options={[
              { label: 'Deutsch', value: 'DE' },
              { label: 'English', value: 'EN' },
            ]}
            onChange={(e) => saveLocale(e.value as Locale)}
          />
        </div>
      </Card>
      <br />

      {user?.identity_provider ? (
        <Card
          title={t('profile.account')}
          subTitle={t('profile.externalHint', { provider: user.identity_provider })}
        >
          <p className="muted">
            {t('profile.externalBody', { provider: user.identity_provider })}
          </p>
        </Card>
      ) : (
      <Card title={t('profile.account')} subTitle={t('profile.accountHint')}>
        <div className="form-field" style={{ maxWidth: 420 }}>
          <label htmlFor="profile-email">{t('common.email')}</label>
          <div className="claim-row">
            <InputText
              id="profile-email"
              value={email}
              style={{ flex: 1 }}
              onChange={(e) => setEmail(e.target.value)}
            />
            <Button
              label={t('common.change')}
              icon="pi pi-check"
              disabled={email === user?.email}
              onClick={saveEmail}
            />
          </div>
        </div>
        <div className="form-field" style={{ maxWidth: 420 }}>
          <label>{t('profile.changePassword')}</label>
          <Password
            value={currentPassword}
            placeholder={t('profile.currentPassword')}
            feedback={false}
            toggleMask
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
          <Password
            value={newPassword}
            placeholder={t('profile.newPassword')}
            toggleMask
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <Password
            value={repeatPassword}
            placeholder={t('profile.repeatPassword')}
            feedback={false}
            toggleMask
            onChange={(e) => setRepeatPassword(e.target.value)}
          />
          {repeatPassword !== '' && newPassword !== repeatPassword && (
            <small className="muted">{t('profile.passwordMismatch')}</small>
          )}
          <div>
            <Button
              label={t('profile.changePassword')}
              icon="pi pi-key"
              disabled={
                !currentPassword || newPassword.length < 8 || newPassword !== repeatPassword
              }
              onClick={savePassword}
            />
          </div>
        </div>
      </Card>
      )}
      <br />

      <Card title={t('profile.dangerZone')}>
        <Button
          label={t('profile.deleteAccount')}
          icon="pi pi-trash"
          severity="danger"
          outlined
          onClick={removeAccount}
        />
      </Card>
    </div>
  )
}
