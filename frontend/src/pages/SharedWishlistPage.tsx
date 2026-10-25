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
import { useListStatus } from '../hooks/useListStatus'
import { EmptyState } from '../components/EmptyState'
import { ErrorState, ListSkeleton, PageHeader } from '../components/PageStates'
import WishLink from '../components/WishLink'
import { useT } from '../i18n'

/** Group-member view of a shared wishlist: shows claims and lets members claim. */
export function SharedWishlistPage() {
  const { groupId, id } = useParams<{ groupId: string; id: string }>()
  const [wishlist, setWishlist] = useState<Wishlist | null>(null)
  const { showError } = useApiToast()
  const t = useT()

  const fetcher = useCallback((page: PageQuery) => listWishes(id!, page), [id])
  const list = useListStatus<SharedWish>(
    fetcher as (page: PageQuery) => Promise<ListResponse<SharedWish>>,
    showError,
  )

  useEffect(() => {
    if (id) getWishlist(id).then(setWishlist).catch(showError)
  }, [id])

  return (
    <div>
      <p>
        <Link to={`/groups/${groupId}`}>{t('groups.backToGroup')}</Link>
      </p>
      <PageHeader title={wishlist?.name ?? '…'} />
      {list.failed ? (
        <ErrorState onRetry={list.reload} />
      ) : list.initial ? (
        <ListSkeleton />
      ) : list.total === 0 ? (
        <EmptyState
          icon="pi pi-inbox"
          title={t('empty.shared.title')}
          text={t('empty.shared.text')}
        />
      ) : (
      <div className="card-grid">
        {list.items.map((wish) => (
          <Card
            key={wish.id}
            className="entity-card"
            title={<Link to={`/wishes/${wish.id}`} state={{ nav: 'groups' }}>{wish.name}</Link>}
          >
            {wish.description && <p>{wish.description}</p>}
            <Tag
              value={
                wish.quantity === null
                  ? t('common.unlimited')
                  : t('wishes.remaining', {
                      remaining: wish.remaining ?? 0,
                      quantity: wish.quantity,
                    })
              }
              severity={wish.remaining === 0 ? 'warning' : undefined}
            />
            <div className="wish-card-links">
              {wish.links.map((link) => (
                <WishLink key={link.id ?? link.url} link={link} reportable={'claims' in wish} />
              ))}
            </div>
            <ClaimControls wish={wish} onChanged={list.reload} />
          </Card>
        ))}
      </div>
      )}
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
