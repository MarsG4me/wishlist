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
import { CardActions } from '../components/CardActions'
import { ListControls } from '../components/ListControls'
import { useApiToast } from '../hooks/useApiToast'
import { useListStatus } from '../hooks/useListStatus'
import { EmptyState } from '../components/EmptyState'
import { ErrorState, ListSkeleton, PageHeader } from '../components/PageStates'
import WishLink from '../components/WishLink'
import { ApiError } from '../api/errors'
import { tError, useT } from '../i18n'

/** Own wishlist: full wish CRUD, structurally no claim information. */
export function WishlistDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [wishlist, setWishlist] = useState<Wishlist | null>(null)
  const [editing, setEditing] = useState<OwnWish | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  const fetcher = useCallback((page: PageQuery) => listWishes(id!, page), [id])
  const list = useListStatus<OwnWish>(
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

  const openCreate = () => {
    setEditing(null)
    setDialogOpen(true)
  }

  const save = async (input: WishInput) => {
    try {
      if (editing) {
        const updated = await updateWish(editing.id, input)
        if (updated.existing_claims_affected) {
          confirmDialog({
            message: t('wishes.existingClaimsMessage'),
            header: t('wishes.existingClaimsHeader'),
            icon: 'pi pi-info-circle',
            rejectClassName: 'hidden',
            acceptLabel: t('common.understood'),
          })
        }
      } else if (id) {
        await createWish(id, input)
      }
      showSuccess(t('common.saved'))
      reload()
    } catch (e) {
      // a link error is shown inline in the form instead
      if (!(e instanceof ApiError && e.linkIndex !== undefined)) showError(e)
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
          header: t('wishes.deleteHeader'),
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
        <Link to="/">{t('wishlists.backLink')}</Link>
      </p>
      <PageHeader
        title={wishlist?.name ?? '…'}
        action={<Button label={t('wishes.new')} icon="pi pi-plus" onClick={openCreate} />}
      />
      {list.failed ? (
        <ErrorState onRetry={list.reload} />
      ) : list.initial ? (
        <ListSkeleton />
      ) : list.total === 0 ? (
        <EmptyState
          icon="pi pi-gift"
          title={t('empty.wishes.title')}
          text={t('empty.wishes.text')}
          action={{ label: t('empty.wishes.action'), icon: 'pi pi-plus', onClick: openCreate }}
        />
      ) : (
      <div className="card-grid">
        {wishes.map((wish) => (
          <Card
            key={wish.id}
            className="entity-card"
            title={<Link to={`/wishes/${wish.id}`}>{wish.name}</Link>}
          >
            <CardActions
              items={[
                {
                  label: t('common.edit'),
                  icon: 'pi pi-pencil',
                  command: () => {
                    setEditing(wish)
                    setDialogOpen(true)
                  },
                },
                { separator: true },
                {
                  label: t('common.delete'),
                  icon: 'pi pi-trash',
                  className: 'menu-item-danger',
                  command: () => remove(wish),
                },
              ]}
            />
            {wish.description && <p>{wish.description}</p>}
            <Tag
              value={
                wish.quantity === null ? t('common.unlimited') : t('wishes.once')
              }
            />
            <div className="wish-card-links">
              {wish.links.map((link, i) => (
                <WishLink key={i} link={link} />
              ))}
            </div>
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
      <WishFormDialog
        visible={dialogOpen}
        wish={editing}
        onSave={save}
        onHide={() => setDialogOpen(false)}
      />
    </div>
  )
}
