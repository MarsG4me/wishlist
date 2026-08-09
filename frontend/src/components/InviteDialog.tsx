import { Dialog } from 'primereact/dialog'
import { Button } from 'primereact/button'
import { QRCodeSVG } from 'qrcode.react'
import type { Invite } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'
import { useT } from '../i18n'

/** Shows a fresh invite as link + QR code for easy sharing. */
export function InviteDialog({ invite, onHide }: { invite: Invite | null; onHide: () => void }) {
  const { showSuccess } = useApiToast()
  const t = useT()
  const link = invite ? `${window.location.origin}/join/${invite.code}` : ''

  return (
    <Dialog
      header={t('invites.dialogHeader')}
      visible={invite !== null}
      style={{ width: 'min(420px, 95vw)' }}
      onHide={onHide}
    >
      {invite && (
        <div className="invite-code-box">
          <QRCodeSVG value={link} size={196} />
          <code>{link}</code>
          <p className="muted">
            {invite.max_uses !== null
              ? t('invites.validityLimited', { count: invite.max_uses })
              : t('invites.validity')}
          </p>
          <Button
            label={t('invites.copyLink')}
            icon="pi pi-copy"
            onClick={() =>
              navigator.clipboard.writeText(link).then(() => showSuccess(t('common.copied')))
            }
          />
        </div>
      )}
    </Dialog>
  )
}
