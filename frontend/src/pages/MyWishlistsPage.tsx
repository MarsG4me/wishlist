import { useCallback, useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { Link } from 'react-router-dom'
import { createWishlist, deleteWishlist, listWishlists, renameWishlist } from '../api/wishlists'
import type { PageQuery, Wishlist } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'
import { CardActions } from '../components/CardActions'
import { ListControls } from '../components/ListControls'
import { useT } from '../i18n'

export function MyWishlistsPage() {
  const [editing, setEditing] = useState<Wishlist | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const { showError, showSuccess } = useApiToast()
  const t = useT()

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
      showSuccess(t('common.saved'))
      list.reload()
    } catch (e) {
      showError(e)
    }
  }

  const remove = (wishlist: Wishlist) =>
    confirmDialog({
      message: t('wishlists.deleteMessage', { name: wishlist.name }),
      header: t('wishlists.deleteHeader'),
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
        <h1>{t('wishlists.title')}</h1>
        <Button label={t('wishlists.new')} icon="pi pi-plus" onClick={openCreate} />
      </div>
      <div className="card-grid">
        {list.items.map((wishlist) => (
          <Card
            key={wishlist.id}
            className="entity-card"
            title={<Link to={`/wishlists/${wishlist.id}`}>{wishlist.name}</Link>}
          >
            <CardActions
              items={[
                { label: t('common.rename'), icon: 'pi pi-pencil', command: () => openRename(wishlist) },
                { separator: true },
                {
                  label: t('common.delete'),
                  icon: 'pi pi-trash',
                  className: 'menu-item-danger',
                  command: () => remove(wishlist),
                },
              ]}
            />
          </Card>
        ))}
        {list.total === 0 && !list.loading && (
          <p className="muted">{t('wishlists.empty')}</p>
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
        header={editing ? t('wishlists.renameHeader') : t('wishlists.newHeader')}
        visible={dialogOpen}
        style={{ width: 'min(400px, 95vw)' }}
        onHide={() => setDialogOpen(false)}
      >
        <div className="form-field">
          <label htmlFor="list-name">{t('common.name')}</label>
          <InputText id="list-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        </div>
        <Button label={t('common.save')} icon="pi pi-check" disabled={!name.trim()} onClick={save} />
      </Dialog>
    </div>
  )
}
