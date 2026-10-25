import { useEffect, useRef } from 'react'
import { Paginator } from 'primereact/paginator'
import { Dropdown } from 'primereact/dropdown'
import { ProgressSpinner } from 'primereact/progressspinner'
import { PAGE_SIZE_OPTIONS } from '../hooks/usePagedList'
import { useT } from '../i18n'

interface Props {
  total: number
  limit: number
  offset: number
  loading: boolean
  isMobile: boolean
  hasMore: boolean
  onLimit: (limit: number) => void
  onOffset: (offset: number) => void
  onLoadMore: () => void
}

/**
 * Paging controls for a list. Phones get a sentinel that pulls the next page into
 * view while scrolling; wider screens get an explicit paginator. Page size is the
 * requester's choice in both cases (the backend clamps it to 1..100).
 */
export function ListControls({
  total,
  limit,
  offset,
  loading,
  isMobile,
  hasMore,
  onLimit,
  onOffset,
  onLoadMore,
}: Props) {
  const sentinel = useRef<HTMLDivElement>(null)
  const t = useT()

  useEffect(() => {
    if (!isMobile || !hasMore || loading || !sentinel.current) return
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) onLoadMore()
    })
    observer.observe(sentinel.current)
    return () => observer.disconnect()
  }, [isMobile, hasMore, loading, onLoadMore])

  if (total === 0) return null

  const sizePicker = (
    <span className="list-controls-size">
      <label htmlFor="page-size">{t('common.perPage')}</label>
      <Dropdown
        inputId="page-size"
        value={limit}
        options={PAGE_SIZE_OPTIONS.map((size) => ({ label: String(size), value: size }))}
        onChange={(e) => onLimit(e.value)}
      />
    </span>
  )

  if (isMobile) {
    return (
      <div className="list-controls">
        {sizePicker}
        <div ref={sentinel} className="list-sentinel">
          {loading && <ProgressSpinner style={{ width: 32, height: 32 }} />}
          {!hasMore && total > 0 && (
            <span className="muted">{t('common.inTotal', { total })}</span>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="list-controls">
      <Paginator
        first={offset}
        rows={limit}
        totalRecords={total}
        onPageChange={(e) => onOffset(e.first)}
      />
      {sizePicker}
    </div>
  )
}
