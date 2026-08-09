import { useCallback, useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Tag } from 'primereact/tag'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { getWishlist } from '../api/wishlists'
import { createWish, deleteWish, listWishes, updateWish, type WishInput } from '../api/wishes'
import type { ListResponse, OwnWish, PageQuery, Wishlist } from '../api/types'
import { WishFormDialog } from '../components/WishFormDialog'
import { ListControls } from '../components/ListControls'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'
import { ApiError } from '../api/errors'
import { tError } from '../i18n'

/** Own wishlist: full wish CRUD, structurally no claim information. */
export function WishlistDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [wishlist, setWishlist] = useState<Wishlist | null>(null)
  const [editing, setEditing] = useState<OwnWish | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const { showError, showSuccess } = useApiToast()

  const fetcher = useCallback((page: PageQuery) => listWishes(id!, page), [id])
  const list = usePagedList<OwnWish>(
    fetcher as (page: PageQuery) => Promise<ListResponse<OwnWish>>,
    showError,
  )
  const wishes = list.items

  const reload = () => {
    if (!id) return
    getWishlist(id).then(setWishlist).catch(showError)
    list.reload()
  }
  useEffect(() => {
    if (id) getWishlist(id).then(setWishlist).catch(showError)
  }, [id])

  const save = async (input: WishInput) => {
    try {
      if (editing) {
        const updated = await updateWish(editing.id, input)
        if (updated.existing_claims_affected) {
          confirmDialog({
            message:
              'This wish already has claims. They stay valid, and the claimers are NOT notified of your change.',
            header: 'Existing claims',
            icon: 'pi pi-info-circle',
            rejectClassName: 'hidden',
            acceptLabel: 'Understood',
          })
        }
      } else if (id) {
        await createWish(id, input)
      }
      showSuccess('Saved')
      reload()
    } catch (e) {
      showError(e)
      throw e
    }
  }

  const remove = async (wish: OwnWish) => {
    try {
      await deleteWish(wish.id)
      reload()
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'WISH_HAS_CLAIMS') {
        confirmDialog({
          message: tError('WISH_HAS_CLAIMS'),
          header: 'Delete wish',
          icon: 'pi pi-exclamation-triangle',
          acceptClassName: 'p-button-danger',
          accept: async () => {
            try {
              await deleteWish(wish.id, true)
              reload()
            } catch (err) {
              showError(err)
            }
          },
        })
      } else {
        showError(e)
      }
    }
  }

  return (
    <div>
      <ConfirmDialog />
      <p>
        <Link to="/">← My wishlists</Link>
      </p>
      <div className="page-header">
        <h1>{wishlist?.name ?? '…'}</h1>
        <Button
          label="New wish"
          icon="pi pi-plus"
          onClick={() => {
            setEditing(null)
            setDialogOpen(true)
          }}
        />
      </div>
      <div className="card-grid">
        {wishes.map((wish) => (
          <Card key={wish.id} title={wish.name}>
            {wish.description && <p>{wish.description}</p>}
            <Tag value={wish.quantity === null ? 'unlimited' : `${wish.quantity}×`} />
            <div className="wish-card-links">
              {wish.links.map((link, i) => (
                <a key={i} href={link.url} target="_blank" rel="noreferrer">
                  {link.label || new URL(link.url).hostname}
                </a>
              ))}
            </div>
            <div className="claim-row">
              <Button
                icon="pi pi-pencil"
                text
                onClick={() => {
                  setEditing(wish)
                  setDialogOpen(true)
                }}
              />
              <Button icon="pi pi-trash" text severity="danger" onClick={() => remove(wish)} />
            </div>
          </Card>
        ))}
        {list.total === 0 && !list.loading && <p className="muted">No wishes yet.</p>}
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
      <WishFormDialog
        visible={dialogOpen}
        wish={editing}
        onSave={save}
        onHide={() => setDialogOpen(false)}
      />
    </div>
  )
}
