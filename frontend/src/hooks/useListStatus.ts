import { useEffect, useState } from 'react'
import type { ListResponse, PageQuery } from '../api/types'
import { usePagedList } from './usePagedList'

/**
 * usePagedList plus `initial` (first fetch not settled yet) and `failed` (last fetch errored).
 * Lets pages show a skeleton or an error state instead of a blank or falsely empty view.
 */
export function useListStatus<T>(
  fetcher: (page: PageQuery) => Promise<ListResponse<T>>,
  onError: (error: unknown) => void,
  initialLimit?: number,
  enabled = true,
) {
  const [failed, setFailed] = useState(false)
  const [started, setStarted] = useState(false)
  const [settled, setSettled] = useState(false)
  const list = usePagedList<T>(
    fetcher,
    (e) => {
      setFailed(true)
      onError(e)
    },
    initialLimit,
    enabled,
  )
  useEffect(() => {
    if (list.loading) {
      setStarted(true)
      setFailed(false)
    } else if (started) setSettled(true)
  }, [list.loading])
  return { ...list, failed, initial: enabled && !settled && !failed }
}
