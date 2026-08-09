import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card } from 'primereact/card'
import { Tag } from 'primereact/tag'
import { getWishlist } from '../api/wishlists'
import { listWishes } from '../api/wishes'
import type { SharedWish, Wishlist } from '../api/types'
import { ClaimControls } from '../components/ClaimButton'
import { useApiToast } from '../hooks/useApiToast'

/** Group-member view of a shared wishlist: shows claims and lets members claim. */
export function SharedWishlistPage() {
  const { groupId, id } = useParams<{ groupId: string; id: string }>()
  const [wishlist, setWishlist] = useState<Wishlist | null>(null)
  const [wishes, setWishes] = useState<SharedWish[]>([])
  const { showError } = useApiToast()

  const reload = () => {
    if (!id) return
    getWishlist(id).then(setWishlist).catch(showError)
    listWishes(id)
      .then((list) => setWishes(list as SharedWish[]))
      .catch(showError)
  }
  useEffect(reload, [id])

  return (
    <div>
      <p>
        <Link to={`/groups/${groupId}`}>← Group</Link>
      </p>
      <div className="page-header">
        <h1>{wishlist?.name ?? '…'}</h1>
      </div>
      <div className="card-grid">
        {wishes.map((wish) => (
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
              {wish.links.map((link, i) => (
                <a key={i} href={link.url} target="_blank" rel="noreferrer">
                  {link.label || new URL(link.url).hostname}
                </a>
              ))}
            </div>
            <ClaimControls wish={wish} onChanged={reload} />
          </Card>
        ))}
        {wishes.length === 0 && <p className="muted">This wishlist is empty.</p>}
      </div>
    </div>
  )
}
