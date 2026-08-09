import { Dialog } from 'primereact/dialog'
import { Button } from 'primereact/button'
import { QRCodeSVG } from 'qrcode.react'
import type { Invite } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'

/** Shows a fresh invite as link + QR code for easy sharing. */
export function InviteDialog({ invite, onHide }: { invite: Invite | null; onHide: () => void }) {
  const { showSuccess } = useApiToast()
  const link = invite ? `${window.location.origin}/join/${invite.code}` : ''

  return (
    <Dialog header="Invite link" visible={invite !== null} style={{ width: 'min(420px, 95vw)' }} onHide={onHide}>
      {invite && (
        <div className="invite-code-box">
          <QRCodeSVG value={link} size={196} />
          <code>{link}</code>
          <p className="muted">
            Valid 24h{invite.max_uses !== null && `, max ${invite.max_uses} uses`}.
          </p>
          <Button
            label="Copy link"
            icon="pi pi-copy"
            onClick={() => navigator.clipboard.writeText(link).then(() => showSuccess('Copied'))}
          />
        </div>
      )}
    </Dialog>
  )
}
