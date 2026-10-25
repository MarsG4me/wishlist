import { useCallback, useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { Link, useNavigate } from 'react-router-dom'
import { createWishlist, deleteWishlist, listWishlists, renameWishlist } from '../api/wishlists'
import type { PageQuery, Wishlist } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'
import { useListStatus } from '../hooks/useListStatus'
import { CardActions } from '../components/CardActions'
import { ListControls } from '../components/ListControls'
import { ErrorState, ListSkeleton, PageHeader } from '../components/PageStates'
import { OnboardingCard } from '../components/onboarding/OnboardingCard'
import { useT } from '../i18n'

export function MyWishlistsPage() {
  const [editing, setEditing] = useState<Wishlist | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const { showError, showSuccess } = useApiToast()
  const t = useT()
  const navigate = useNavigate()

  const fetcher = useCallback((page: PageQuery) => listWishlists(page), [])
  const list = useListStatus<Wishlist>(fetcher, showError)

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
      const isFirst = !editing && list.total === 0
      let created: Wishlist | null = null
      if (editing) await renameWishlist(editing.id, name)
      else created = await createWishlist(name)
      setDialogOpen(false)
      showSuccess(t('common.saved'))
      // the first wishlist leads straight into adding its first wish
      if (isFirst && created) {
        navigate(`/wishlists/${created.id}`)
        return
      }
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
      <PageHeader
        title={t('wishlists.title')}
        subtitle={t('wishlists.subtitle')}
        action={<Button label={t('wishlists.new')} icon="pi pi-plus" onClick={openCreate} />}
      />
      {!list.initial && !list.failed && (
        <OnboardingCard wishlistTotal={list.total} onCreateWishlist={openCreate} />
      )}
      {list.failed ? (
        <ErrorState onRetry={list.reload} />
      ) : list.initial ? (
        <ListSkeleton />
      ) : (
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
