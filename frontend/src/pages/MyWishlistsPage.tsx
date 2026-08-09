import { useCallback, useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { useNavigate } from 'react-router-dom'
import { createWishlist, deleteWishlist, listWishlists, renameWishlist } from '../api/wishlists'
import type { PageQuery, Wishlist } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'
import { ListControls } from '../components/ListControls'

export function MyWishlistsPage() {
  const [editing, setEditing] = useState<Wishlist | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const navigate = useNavigate()
  const { showError, showSuccess } = useApiToast()

  const fetcher = useCallback((page: PageQuery) => listWishlists(page), [])
  const list = usePagedList<Wishlist>(fetcher, showError)

  const openCreate = () => {
    setEditing(null)
    setName('')
    setDialogOpen(true)
  }
  const openRename = (wishlist: Wishlist) => {
    setEditing(wishlist)
    setName(wishlist.name)
    setDialogOpen(true)
  }

  const save = async () => {
    try {
      if (editing) await renameWishlist(editing.id, name)
      else await createWishlist(name)
      setDialogOpen(false)
      showSuccess('Saved')
      list.reload()
    } catch (e) {
      showError(e)
    }
  }

  const remove = (wishlist: Wishlist) =>
    confirmDialog({
      message: `Delete wishlist "${wishlist.name}"? It can be restored within 30 days.`,
      header: 'Delete wishlist',
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await deleteWishlist(wishlist.id)
          list.reload()
        } catch (e) {
          showError(e)
        }
      },
    })

  return (
    <div>
      <ConfirmDialog />
      <div className="page-header">
        <h1>My wishlists</h1>
        <Button label="New wishlist" icon="pi pi-plus" onClick={openCreate} />
      </div>
      <div className="card-grid">
        {list.items.map((wishlist) => (
          <Card key={wishlist.id} title={wishlist.name}>
            <div className="claim-row">
              <Button label="Open" icon="pi pi-arrow-right" text onClick={() => navigate(`/wishlists/${wishlist.id}`)} />
              <Button icon="pi pi-pencil" text onClick={() => openRename(wishlist)} />
              <Button icon="pi pi-trash" text severity="danger" onClick={() => remove(wishlist)} />
            </div>
          </Card>
        ))}
        {list.total === 0 && !list.loading && (
          <p className="muted">No wishlists yet — create your first one.</p>
        )}
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
      <Dialog
        header={editing ? 'Rename wishlist' : 'New wishlist'}
        visible={dialogOpen}
        style={{ width: 'min(400px, 95vw)' }}
        onHide={() => setDialogOpen(false)}
      >
        <div className="form-field">
          <label htmlFor="list-name">Name</label>
          <InputText id="list-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        </div>
        <Button label="Save" icon="pi pi-check" disabled={!name.trim()} onClick={save} />
      </Dialog>
    </div>
  )
}
