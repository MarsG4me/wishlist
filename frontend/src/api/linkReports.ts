import { api, rethrowBlobError } from './client'
import type { LinkBlockedBy, ListResponse, PageQuery } from './types'

export type LinkReportReason = 'MALWARE_PHISHING' | 'SCAM' | 'ILLEGAL' | 'CHILD_ABUSE' | 'ADULT' | 'OTHER'
export type LinkReportStatus = 'OPEN' | 'DISMISSED' | 'ACTIONED' | 'ESCALATED'
export type LinkReportAction = 'DISMISS' | 'BLOCK_LINK' | 'BLOCK_DOMAIN'

export const LINK_REPORT_REASONS: LinkReportReason[] = [
  'MALWARE_PHISHING',
  'SCAM',
  'ILLEGAL',
  'CHILD_ABUSE',
  'ADULT',
  'OTHER',
]

export interface LinkReport {
  id: string
  status: LinkReportStatus
  reason: LinkReportReason
  comment: string | null
  reported_at: string
  link_id: string | null
  url: string | null
  host: string | null
  blocked_by: LinkBlockedBy | null
  wish_id: string | null
  wish_title: string | null
  owner_displayname: string | null
  reporter_displayname: string | null
  decided_at: string | null
}

export interface BlockedDomain {
  id: string
  host: string
  blocked_at: string
  link_report_id: string | null
}

/** Non-owner reports a shared link; the backend answers 204. */
export const reportLink = async (
  linkId: string,
  reason: LinkReportReason,
  comment: string | null,
): Promise<void> => {
  await api.post(`/v1/links/${linkId}/reports`, { reason, comment })
}

export const listLinkReports = async (
  status: LinkReportStatus | null,
  page: PageQuery = {},
): Promise<ListResponse<LinkReport>> =>
  (await api.get('/v1/admin/link-reports', { params: { status: status ?? undefined, ...page } })).data

/** escalate is only accepted together with a block action. */
export const decideLinkReport = async (
  id: string,
  action: LinkReportAction,
  escalate: boolean,
): Promise<LinkReport> =>
  (await api.post(`/v1/admin/link-reports/${id}/decision`, { action, escalate })).data

/** Fetches the authority report of an escalated report with the bearer token and saves it. */
export const downloadAuthorityReport = async (id: string, format: 'json' | 'html'): Promise<void> => {
  const response = await api
    .get(`/v1/admin/link-reports/${id}/authority-report`, { params: { format }, responseType: 'blob' })
    .catch(rethrowBlobError)
  const type = format === 'json' ? 'application/json' : 'text/html'
  const url = URL.createObjectURL(new Blob([response.data], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `authority-report-${id}.${format}`
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export const listBlockedDomains = async (page: PageQuery = {}): Promise<ListResponse<BlockedDomain>> =>
  (await api.get('/v1/admin/blocked-domains', { params: page })).data

export const removeBlockedDomain = async (id: string): Promise<void> => {
  await api.delete(`/v1/admin/blocked-domains/${id}`)
}

export const unblockLink = async (linkId: string): Promise<void> => {
  await api.post(`/v1/admin/links/${linkId}/unblock`)
}
