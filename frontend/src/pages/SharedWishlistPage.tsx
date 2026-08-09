import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card } from 'primereact/card'
import { Tag } from 'primereact/tag'
import { getWishlist } from '../api/wishlists'
import { listWishes } from '../api/wishes'
import type { ListResponse, PageQuery, SharedWish, Wishlist } from '../api/types'
import { ClaimControls } from '../components/ClaimButton'
import { ListControls } from '../components/ListControls'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'

/** Group-member view of a shared wishlist: shows claims and lets members claim. */
export function SharedWishlistPage() {
  const { groupId, id } = useParams<{ groupId: string; id: string }>()
  const [wishlist, setWishlist] = useState<Wishlist | null>(null)
  const { showError } = useApiToast()

  const fetcher = useCallback((page: PageQuery) => listWishes(id!, page), [id])
  const list = usePagedList<SharedWish>(
    fetcher as (page: PageQuery) => Promise<ListResponse<SharedWish>>,
    showError,
  )

  useEffect(() => {
    if (id) getWishlist(id).then(setWishlist).catch(showError)
  }, [id])

  return (
    <div>
      <p>
        <Link to={`/groups/${groupId}`}>← Group</Link>
      </p>
      <div className="page-header">
        <h1>{wishlist?.name ?? '…'}</h1>
      </div>
      <div className="card-grid">
        {list.items.map((wish) => (
          <Card key={wish.id} title={wish.name}>
            {wish.description && <p>{wish.description}</p>}
            <Tag
              value={
                wish.quantity === null
                  ? 'unlimited'
                  : `${wish.remaining} of ${wish.quantity} left`
              }
              severity={wish.remaining === 0 ? 'warning' : undefined}
            />
            <div className="wish-card-links">
              {wish.links.map((link) => (
                <a key={link.url} href={link.url} target="_blank" rel="noreferrer">
                  {link.label || new URL(link.url).hostname}
                </a>
              ))}
            </div>
            <ClaimControls wish={wish} onChanged={list.reload} />
          </Card>
        ))}
        {list.total === 0 && !list.loading && <p className="muted">This wishlist is empty.</p>}
      </div>
      <ListControls
        total={list.total}
        limit={list.limit}
        offset={list.offset}
        loading={list.loading}
        isMobile={list.isMobile}
        hasMore={list.hasMore}
        onLimit={list.setLimit}
        onOffset={list.setOffset}
        onLoadMore={list.loadMore}
      />
    </div>
  )
}
