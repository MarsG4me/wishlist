import { useState } from 'react'
import { Button } from 'primereact/button'
import { Tag } from 'primereact/tag'
import type { WishLink as WishLinkData } from '../api/types'
import { useT } from '../i18n'
import type { UiKey } from '../i18n/ui.en'
import { ReportLinkDialog } from './ReportLinkDialog'

interface Props {
  link: Pick<WishLinkData, 'url' | 'label' | 'id' | 'blocked' | 'blocked_by'>
  /** True for non-owner views: shows the report button. Never set for the owner. */
  reportable?: boolean
}

/** One wish link: owner sees a blocked badge with reason, non-owners get a report button. */
export default function WishLink({ link, reportable = false }: Props) {
  const [reporting, setReporting] = useState(false)
  const t = useT()
  return (
    <span className="wish-link-item">
      <SafeAnchor url={link.url} label={link.label} />
      {link.blocked && (
        <>
          {' '}
          <Tag severity="danger" icon="pi pi-ban" value={t('links.blocked')} />
          {link.blocked_by && (
            <small className="p-error" style={{ display: 'block' }}>
              {t(`links.blockedBy.${link.blocked_by}` as UiKey)}
            </small>
          )}
        </>
      )}
      {reportable && link.id && (
        <>
          <Button
            label={t('links.report')}
            icon="pi pi-flag"
            text
            size="small"
            severity="secondary"
            onClick={() => setReporting(true)}
          />
          <ReportLinkDialog linkId={reporting ? link.id : null} onHide={() => setReporting(false)} />
        </>
      )}
    </span>
  )
}

/** Safe external link: only http(s) URLs become anchors, anything else is plain text. */
function SafeAnchor({ url, label }: { url: string; label?: string | null }) {
  let parsed: URL | null = null
  try {
    parsed = new URL(url)
  } catch {
    parsed = null
  }
  if (!parsed || (parsed.protocol !== 'http:' && parsed.protocol !== 'https:')) {
    return <span>{label || url}</span>
  }
  const host = parsed.hostname.replace(/^www\./, '')
  return (
    <a href={parsed.href} target="_blank" rel="noopener noreferrer nofollow ugc">
      {label || host} <i className="pi pi-external-link" style={{ fontSize: '0.75em' }} />
      {label && <small className="muted"> ({host})</small>}
    </a>
  )
}
