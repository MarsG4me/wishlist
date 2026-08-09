import { useCallback, useEffect, useState } from 'react'
import type { ListResponse, PageQuery } from '../api/types'

export const PAGE_SIZE_OPTIONS = [10, 20, 50, 80, 100]
export const DEFAULT_PAGE_SIZE = 20

/** Phones append while scrolling; on wider screens pages are navigated explicitly. */
export function useIsMobile(): boolean {
  const query = '(max-width: 768px)'
  const [isMobile, setIsMobile] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const media = window.matchMedia(query)
    const onChange = (e: MediaQueryListEvent) => setIsMobile(e.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])
  return isMobile
}

interface PagedList<T> {
  items: T[]
  total: number
  limit: number
  offset: number
  loading: boolean
  isMobile: boolean
  /** True while more entities exist beyond what is currently shown. */
  hasMore: boolean
  setLimit: (limit: number) => void
  setOffset: (offset: number) => void
  loadMore: () => void
  reload: () => void
}

/**
 * Drives a paged list endpoint. Mobile keeps appending to one growing list,
 * desktop replaces the page — the fetch call is identical, only the merge differs.
 *
 * `enabled` guards endpoints the requester may not be allowed to call at all
 * (an admin-only sub-resource, say): while false nothing is fetched, so no
 * permission error can reach the user as a toast.
 */
export function usePagedList<T>(
  fetcher: (page: PageQuery) => Promise<ListResponse<T>>,
  onError: (error: unknown) => void,
  initialLimit = DEFAULT_PAGE_SIZE,
  enabled = true,
): PagedList<T> {
  const isMobile = useIsMobile()
  const [items, setItems] = useState<T[]>([])
  const [total, setTotal] = useState(0)
  const [limit, setLimitState] = useState(initialLimit)
  const [offset, setOffsetState] = useState(0)
  const [loading, setLoading] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    setLoading(true)
    fetcher({ limit, offset })
      .then((response) => {
        if (cancelled) return
        setTotal(response.total)
        // appending only makes sense for the scrolled-in pages of the same list
        setItems((current) =>
          isMobile && response.offset > 0 ? [...current, ...response.items] : response.items,
        )
      })
      .catch(onError)
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [limit, offset, reloadToken, isMobile, enabled])

  const setLimit = useCallback((next: number) => {
    setLimitState(next)
    setOffsetState(0)
  }, [])

  const setOffset = useCallback((next: number) => setOffsetState(next), [])

  const loadMore = useCallback(() => {
    setOffsetState((current) => current + limit)
  }, [limit])

  const reload = useCallback(() => {
    setOffsetState(0)
    setReloadToken((token) => token + 1)
  }, [])

  return {
    items,
    total,
    limit,
    offset,
    loading,
    isMobile,
    hasMore: items.length < total,
    setLimit,
    setOffset,
    loadMore,
    reload,
  }
}
