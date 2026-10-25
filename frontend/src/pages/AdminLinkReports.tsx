import { useCallback, useEffect, useRef, useState } from 'react'
import { Card } from 'primereact/card'
import { Button } from 'primereact/button'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Dropdown } from 'primereact/dropdown'
import { Tag } from 'primereact/tag'
import { Checkbox } from 'primereact/checkbox'
import { Dialog } from 'primereact/dialog'
import {
  decideLinkReport,
  downloadAuthorityReport,
  listBlockedDomains,
  listLinkReports,
  removeBlockedDomain,
  unblockLink,
  type BlockedDomain,
  type LinkReport,
  type LinkReportAction,
  type LinkReportStatus,
} from '../api/linkReports'
import type { PageQuery } from '../api/types'
import { hasAdminRole } from '../auth/keycloak'
import { ListControls } from '../components/ListControls'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'
import { useT } from '../i18n'
import type { UiKey } from '../i18n/ui.en'

const STATUSES: LinkReportStatus[] = ['OPEN', 'DISMISSED', 'ACTIONED', 'ESCALATED']
const STATUS_SEVERITY = { OPEN: 'warning', DISMISSED: undefined, ACTIONED: 'success', ESCALATED: 'danger' } as const

/** Admin sections for reported links and blocked domains. */
export function AdminLinkReports() {
  const [status, setStatus] = useState<LinkReportStatus | null>('OPEN')
  const [target, setTarget] = useState<LinkReport | null>(null)
  const [action, setAction] = useState<LinkReportAction>('DISMISS')
  const [escalate, setEscalate] = useState(false)
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  const reportFetcher = useCallback((page: PageQuery) => listLinkReports(status, page), [status])
  const isAdmin = hasAdminRole()
  const reports = usePagedList(reportFetcher, showError, undefined, isAdmin)
  const domains = usePagedList(listBlockedDomains, showError, undefined, isAdmin)

  // the paged list only refetches on page changes, so a filter change reloads explicitly
  const mounted = useRef(false)
  useEffect(() => {
    if (mounted.current && isAdmin) reports.reload()
    mounted.current = true
  }, [status])

  const openDecision = (report: LinkReport) => {
    setTarget(report)
    setAction('DISMISS')
    setEscalate(false)
  }

  const decide = async () => {
    if (!target) return
    try {
      await decideLinkReport(target.id, action, escalate && action !== 'DISMISS')
      showSuccess(t('admin.reports.decided'))
      setTarget(null)
      reports.reload()
      domains.reload()
    } catch (e) {
      showError(e)
    }
  }

  const download = async (report: LinkReport, format: 'json' | 'html') => {
    try {
      await downloadAuthorityReport(report.id, format)
      showSuccess(t('admin.reports.downloaded'))
    } catch (e) {
      showError(e)
    }
  }

  const unblock = async (report: LinkReport) => {
    if (!report.link_id) return
    try {
      await unblockLink(report.link_id)
      showSuccess(t('admin.reports.unblocked'))
      reports.reload()
    } catch (e) {
      showError(e)
    }
  }

  const removeDomain = async (domain: BlockedDomain) => {
    try {
      await removeBlockedDomain(domain.id)
      showSuccess(t('admin.domains.removed'))
      domains.reload()
    } catch (e) {
      showError(e)
    }
  }

  const statusOptions = [
    { label: t('admin.reports.statusAll'), value: null },
    ...STATUSES.map((value) => ({ label: t(`admin.reports.status.${value}` as UiKey), value })),
  ]

  return (
    <>
      <Card
        title={t('admin.reports.title')}
        subTitle={t('admin.reports.hint')}
        id="admin-link-reports"
        style={{ marginTop: '1.5rem' }}
      >
        <div className="claim-row" style={{ marginBottom: '1rem' }}>
          <label htmlFor="report-status">{t('admin.reports.status')}</label>
          <Dropdown
            inputId="report-status"
            value={status}
            options={statusOptions}
            onChange={(e) => setStatus(e.value)}
          />
        </div>
        <DataTable value={reports.items} dataKey="id" emptyMessage={t('admin.reports.none')}>
          <Column
            header={t('admin.reports.status')}
            body={(r: LinkReport) => (
              <Tag value={t(`admin.reports.status.${r.status}` as UiKey)} severity={STATUS_SEVERITY[r.status]} />
            )}
          />
          <Column
            header={t('admin.reports.reason')}
            body={(r: LinkReport) => (
              <div>
                {t(`links.reason.${r.reason}` as UiKey)}
                {r.comment && <small className="muted" style={{ display: 'block' }}>{r.comment}</small>}
              </div>
            )}
          />
          <Column
            header={t('admin.reports.link')}
            body={(r: LinkReport) =>
              r.url ? (
                <div>
                  <code style={{ wordBreak: 'break-all' }}>{r.url}</code>
                  {r.blocked_by && (
                    <small className="muted" style={{ display: 'block' }}>
                      {t(`links.blockedBy.${r.blocked_by}` as UiKey)}
                    </small>
                  )}
                </div>
              ) : (
                <span className="muted">{t('admin.reports.gone')}</span>
              )
            }
          />
          <Column
            header={t('admin.reports.wish')}
            body={(r: LinkReport) => r.wish_title ?? <span className="muted">{t('common.none')}</span>}
          />
          <Column
            header={t('admin.reports.owner')}
            body={(r: LinkReport) => r.owner_displayname ?? <span className="muted">{t('common.none')}</span>}
          />
          <Column
            header={t('admin.reports.reporter')}
            body={(r: LinkReport) => r.reporter_displayname ?? <span className="muted">{t('common.none')}</span>}
          />
          <Column
            header={t('admin.reports.reportedAt')}
            body={(r: LinkReport) => new Date(r.reported_at).toLocaleDateString()}
          />
          <Column
            header=""
            body={(r: LinkReport) => (
              <div className="claim-row">
                {r.status === 'OPEN' && (
                  <Button label={t('admin.reports.decide')} icon="pi pi-gavel" text onClick={() => openDecision(r)} />
                )}
                {r.status === 'ESCALATED' && (
                  <>
                    <Button
                      label={t('admin.reports.downloadJson')}
                      icon="pi pi-download"
                      text
                      onClick={() => download(r, 'json')}
                    />
                    <Button
                      label={t('admin.reports.downloadHtml')}
                      icon="pi pi-download"
                      text
                      onClick={() => download(r, 'html')}
                    />
                  </>
                )}
                {(r.status === 'ACTIONED' || r.status === 'ESCALATED') && r.blocked_by === 'ADMIN' && r.link_id && (
                  <Button label={t('admin.reports.unblock')} icon="pi pi-lock-open" text onClick={() => unblock(r)} />
                )}
              </div>
            )}
          />
        </DataTable>
        <ListControls
          total={reports.total}
          limit={reports.limit}
          offset={reports.offset}
          loading={reports.loading}
          isMobile={reports.isMobile}
          hasMore={reports.hasMore}
          onLimit={reports.setLimit}
          onOffset={reports.setOffset}
          onLoadMore={reports.loadMore}
        />
      </Card>

      <Card title={t('admin.domains.title')} id="admin-blocked-domains" style={{ marginTop: '1.5rem' }}>
        <DataTable value={domains.items} dataKey="id" emptyMessage={t('admin.domains.none')}>
          <Column field="host" header={t('admin.domains.host')} />
          <Column
            header={t('admin.domains.blockedAt')}
            body={(d: BlockedDomain) => new Date(d.blocked_at).toLocaleDateString()}
          />
          <Column
            header=""
            body={(d: BlockedDomain) => (
              <Button
                label={t('common.remove')}
                icon="pi pi-trash"
                text
                severity="danger"
                onClick={() => removeDomain(d)}
              />
            )}
          />
        </DataTable>
        <ListControls
          total={domains.total}
          limit={domains.limit}
          offset={domains.offset}
          loading={domains.loading}
          isMobile={domains.isMobile}
          hasMore={domains.hasMore}
          onLimit={domains.setLimit}
          onOffset={domains.setOffset}
          onLoadMore={domains.loadMore}
        />
      </Card>

      <Dialog
        header={t('admin.reports.decisionHeader')}
        visible={target !== null}
        style={{ width: 'min(480px, 95vw)' }}
        onHide={() => setTarget(null)}
      >
        {target?.url && <p><code style={{ wordBreak: 'break-all' }}>{target.url}</code></p>}
        <div className="form-field">
          {(['DISMISS', 'BLOCK_LINK', 'BLOCK_DOMAIN'] as LinkReportAction[]).map((value) => (
            <label key={value} className="inline-check" style={{ display: 'flex' }}>
              <input
                type="radio"
                name="report-action"
                checked={action === value}
                onChange={() => setAction(value)}
              />
              {value === 'DISMISS' && t('admin.reports.dismiss')}
              {value === 'BLOCK_LINK' && t('admin.reports.blockLink')}
              {value === 'BLOCK_DOMAIN' && t('admin.reports.blockDomain', { host: target?.host ?? '' })}
            </label>
          ))}
        </div>
        <div className="form-field">
          <label className="inline-check">
            <Checkbox
              checked={escalate && action !== 'DISMISS'}
              disabled={action === 'DISMISS'}
              onChange={(e) => setEscalate(!!e.checked)}
            />
            {t('admin.reports.escalate')}
          </label>
        </div>
        <Button label={t('admin.reports.apply')} icon="pi pi-check" onClick={decide} />
      </Dialog>
    </>
  )
}
