import { useEffect, useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { useNavigate } from 'react-router-dom'
import { createWishlist, deleteWishlist, listWishlists, renameWishlist } from '../api/wishlists'
import type { Wishlist } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'

export function MyWishlistsPage() {
  const [wishlists, setWishlists] = useState<Wishlist[]>([])
  const [editing, setEditing] = useState<Wishlist | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const navigate = useNavigate()
  const { showError, showSuccess } = useApiToast()

  const reload = () => listWishlists().then(setWishlists).catch(showError)
  useEffect(() => {
    reload()
  }, [])

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
      reload()
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
          reload()
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
        {wishlists.map((wishlist) => (
          <Card key={wishlist.id} title={wishlist.name}>
            <div className="claim-row">
              <Button label="Open" icon="pi pi-arrow-right" text onClick={() => navigate(`/wishlists/${wishlist.id}`)} />
              <Button icon="pi pi-pencil" text onClick={() => openRename(wishlist)} />
              <Button icon="pi pi-trash" text severity="danger" onClick={() => remove(wishlist)} />
            </div>
          </Card>
        ))}
        {wishlists.length === 0 && <p className="muted">No wishlists yet — create your first one.</p>}
      </div>
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
