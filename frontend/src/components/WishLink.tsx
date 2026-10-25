/** Safe external link: only http(s) URLs become anchors, anything else is plain text. */
export default function WishLink({ url, label }: { url: string; label?: string | null }) {
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
