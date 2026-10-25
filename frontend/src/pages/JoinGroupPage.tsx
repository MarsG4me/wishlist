import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { joinGroup, previewInvite } from '../api/memberships'
import { ApiError } from '../api/errors'
import type { InvitePreview } from '../api/types'
import { keycloak } from '../auth/keycloak'
import { useApiToast } from '../hooks/useApiToast'
import { tError, useLanguage, useT } from '../i18n'

/** Public invite landing page: shows the group and inviter, joining needs an explicit click. */
export function JoinGroupPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const { showError } = useApiToast()
  const [preview, setPreview] = useState<InvitePreview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [alreadyMember, setAlreadyMember] = useState(false)
  const t = useT()
  const language = useLanguage()
  const loggedIn = keycloak.authenticated === true

  useEffect(() => {
    if (!code) return
    previewInvite(code)
      .then((p) => {
        setPreview(p)
        setAlreadyMember(p.already_member === true)
      })
      .catch((e) => setError(tError(e instanceof ApiError ? e.errorCode : 'INTERNAL_ERROR')))
  }, [code])

  const join = async () => {
    if (!code) return
    setBusy(true)
    try {
      const membership = await joinGroup(code)
      navigate(`/groups/${membership.group_id}`, { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'ALREADY_MEMBER') setAlreadyMember(true)
      else showError(e)
    } finally {
      setBusy(false)
    }
  }

  const redirectUri = window.location.href
  const statusMessage = {
    expired: t('join.expired'),
    revoked: t('join.revoked'),
    exhausted: t('join.exhausted'),
  }

  let body
  if (error) body = <p>{error}</p>
  else if (!preview) body = <p className="muted">…</p>
  else {
    body = (
      <>
        {preview.status === 'valid' && (
          <p>
            {preview.creator_displayname
              ? t('join.invitedBy', { name: preview.creator_displayname })
              : t('join.invitedGeneric')}
          </p>
        )}
        {preview.status === 'valid' && (
          <p className="muted">
            {preview.member_count === 1
              ? t('join.membersOne')
              : t('join.members', { count: preview.member_count ?? 0 })}
            {preview.expires_at &&
              ` · ${t('join.expires', { date: new Date(preview.expires_at).toLocaleString(language) })}`}
          </p>
        )}
        {preview.status !== 'valid' && <p>{statusMessage[preview.status]}</p>}
        {preview.status === 'valid' && loggedIn && alreadyMember && (
          <>
            <p>{t('join.alreadyMember')}</p>
            <Button label={t('join.openGroup')} icon="pi pi-users" onClick={() => navigate(preview.group_id ? `/groups/${preview.group_id}` : '/groups')}
            />
          </>
        )}
        {preview.status === 'valid' && loggedIn && !alreadyMember && (
          <Button label={t('join.join')} icon="pi pi-user-plus" loading={busy} onClick={join} />
        )}
        {preview.status === 'valid' && !loggedIn && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
            <Button
              label={t('join.loginAndJoin')}
              icon="pi pi-sign-in"
              onClick={() => keycloak.login({ redirectUri })}
            />
            <Button
              label={t('join.registerAndJoin')}
              icon="pi pi-user-plus"
              outlined
              onClick={() => keycloak.register({ redirectUri })}
            />
          </div>
        )}
      </>
    )
  }

  return (
    <main style={{ maxWidth: 480, margin: '2rem auto', padding: '0 1rem' }}>
      <div className="app-brand" style={{ justifyContent: 'center', marginBottom: '1rem' }}>
        <i className="pi pi-gift" /> {t('app.name')}
      </div>
      <Card title={preview?.group_name ?? t('join.invitedGeneric')}>{body}</Card>
    </main>
  )
}
