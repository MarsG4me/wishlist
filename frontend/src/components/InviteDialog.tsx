import { Dialog } from 'primereact/dialog'
import { Button } from 'primereact/button'
import { QRCodeSVG } from 'qrcode.react'
import type { Invite } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'
import { useT } from '../i18n'

/** Shows a fresh invite as link + QR code for easy sharing. */
export function InviteDialog({
  invite,
  groupName = '',
  onHide,
}: {
  invite: Invite | null
  groupName?: string
  onHide: () => void
}) {
  const { showSuccess } = useApiToast()
  const t = useT()
  const link = invite ? `${window.location.origin}/join/${invite.code}` : ''
  const message = t('invites.message', { name: groupName, link })
  const enc = encodeURIComponent
  const targets = [
    { label: 'WhatsApp', icon: 'pi pi-whatsapp', href: `https://wa.me/?text=${enc(message)}` },
    {
      label: 'Telegram',
      icon: 'pi pi-telegram',
      href: `https://t.me/share/url?url=${enc(link)}&text=${enc(message)}`,
    },
    {
      label: t('invites.viaEmail'),
      icon: 'pi pi-envelope',
      href: `mailto:?subject=${enc(t('invites.shareTitle'))}&body=${enc(message)}`,
    },
    { label: t('invites.viaSms'), icon: 'pi pi-comment', href: `sms:?&body=${enc(message)}` },
  ]
  const qrSize = window.innerHeight < 800 ? 128 : 196
  const copy = (text: string) =>
    navigator.clipboard.writeText(text).then(() => showSuccess(t('common.copied')))
  const share = () =>
    navigator.share({ title: t('invites.shareTitle'), text: message, url: link }).catch(() => {})

  return (
    <Dialog
      header={t('invites.dialogHeader')}
      visible={invite !== null}
      style={{ width: 'min(420px, 95vw)', maxHeight: '95vh' }}
      onHide={onHide}
    >
      {invite && (
        <div className="invite-code-box">
          <QRCodeSVG value={link} size={qrSize} />
          <code>{link}</code>
          <p className="muted">
            {invite.max_uses !== null
              ? t('invites.validityLimited', { count: invite.max_uses })
              : t('invites.validity')}
          </p>
          <Button
            label={t('invites.copyLink')}
            icon="pi pi-copy"
            onClick={() => copy(link)}
          />
          {typeof navigator.share === 'function' && (
            <Button label={t('invites.share')} icon="pi pi-share-alt" onClick={share} />
          )}
          <Button
            label={t('invites.copyMessage')}
            icon="pi pi-clone"
            outlined
            onClick={() => copy(message)}
          />
          <div className="invite-share-grid">
            {targets.map((target) => (
              <a key={target.label} href={target.href} target="_blank" rel="noopener noreferrer">
                <Button label={target.label} icon={target.icon} size="small" outlined tabIndex={-1} className="w-full" />
              </a>
            ))}
          </div>
        </div>
      )}
    </Dialog>
  )
}
