import { useState } from 'react'
import { Dialog } from 'primereact/dialog'
import { Dropdown } from 'primereact/dropdown'
import { InputTextarea } from 'primereact/inputtextarea'
import { Button } from 'primereact/button'
import { LINK_REPORT_REASONS, reportLink, type LinkReportReason } from '../api/linkReports'
import { ApiError } from '../api/errors'
import { useApiToast } from '../hooks/useApiToast'
import { useT } from '../i18n'
import type { UiKey } from '../i18n/ui.en'

interface Props {
  linkId: string | null // null = closed
  onHide: () => void
}

/** Dialog for a non-owner to report a shared link: fixed reason plus optional comment. */
export function ReportLinkDialog({ linkId, onHide }: Props) {
  const [reason, setReason] = useState<LinkReportReason | null>(null)
  const [comment, setComment] = useState('')
  const [sending, setSending] = useState(false)
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  const close = () => {
    setReason(null)
    setComment('')
    onHide()
  }

  const send = async () => {
    if (!linkId || !reason) return
    setSending(true)
    try {
      await reportLink(linkId, reason, comment.trim() || null)
      showSuccess(t('links.reported'))
      close()
    } catch (e) {
      showError(e)
      // already reported: nothing more to send, so the dialog is done
      if (e instanceof ApiError && e.errorCode === 'LINK_ALREADY_REPORTED') close()
    } finally {
      setSending(false)
    }
  }

  return (
    <Dialog
      header={t('links.reportHeader')}
      visible={linkId !== null}
      style={{ width: 'min(480px, 95vw)' }}
      onHide={close}
    >
      <div className="form-field">
        <label htmlFor="report-reason">{t('links.reportReason')}</label>
        <Dropdown
          inputId="report-reason"
          value={reason}
          options={LINK_REPORT_REASONS.map((value) => ({
            value,
            label: t(`links.reason.${value}` as UiKey),
          }))}
          onChange={(e) => setReason(e.value)}
        />
      </div>
      <div className="form-field">
        <label htmlFor="report-comment">{t('links.reportComment')}</label>
        <InputTextarea
          id="report-comment"
          value={comment}
          maxLength={500}
          rows={3}
          onChange={(e) => setComment(e.target.value)}
        />
      </div>
      <Button
        label={t('links.reportSend')}
        icon="pi pi-flag"
        disabled={!reason || sending}
        onClick={send}
      />
    </Dialog>
  )
}
