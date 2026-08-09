import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Tag } from 'primereact/tag'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { deleteWish, getWish, updateWish, type WishInput } from '../api/wishes'
import type { OwnWish, SharedWish } from '../api/types'
import { ClaimControls } from '../components/ClaimButton'
import { WishFormDialog } from '../components/WishFormDialog'
import { CardActions } from '../components/CardActions'
import { useApiToast } from '../hooks/useApiToast'
import { ApiError } from '../api/errors'
import { tError, useT } from '../i18n'

/** A SharedWish carries claim state; the owner view is structurally free of it. */
const isShared = (wish: OwnWish | SharedWish): wish is SharedWish => 'claims' in wish

/**
 * One wish on its own page — the target every wish card and claim row links to.
 * Which of the two views the backend returns decides what is rendered: the owner
 * edits, everyone else sees and coordinates claims.
 */
export function WishDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [wish, setWish] = useState<OwnWish | SharedWish | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  const reload = useCallback(() => {
    if (!id) return
    getWish(id).then(setWish).catch(showError)
  }, [id])

  useEffect(reload, [reload])

  const save = async (input: WishInput) => {
    if (!id) return
    try {
      const updated = await updateWish(id, input)
      if (updated.existing_claims_affected) {
        confirmDialog({
          message: t('wishes.existingClaimsMessage'),
          header: t('wishes.existingClaimsHeader'),
          icon: 'pi pi-info-circle',
          rejectClassName: 'hidden',
          acceptLabel: t('common.understood'),
        })
      }
      showSuccess(t('common.saved'))
      reload()
    } catch (e) {
      showError(e)
      throw e
    }
  }

  const remove = async () => {
    if (!id || !wish) return
    const gone = () => navigate(`/wishlists/${wish.wishlist_id}`)
    try {
      await deleteWish(id)
      gone()
    } catch (e) {
      if (e instanceof ApiError && e.errorCode === 'WISH_HAS_CLAIMS') {
        confirmDialog({
          message: tError('WISH_HAS_CLAIMS'),
          header: t('wishes.deleteHeader'),
          icon: 'pi pi-exclamation-triangle',
          acceptClassName: 'p-button-danger',
          accept: async () => {
            try {
              await deleteWish(id, true)
              gone()
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

  if (!wish) return <p className="muted">…</p>

  const owned = !isShared(wish)
  return (
    <div>
      <ConfirmDialog />
      <p>
        <Button label={t('common.back')} text onClick={() => navigate(-1)} />
      </p>
      <div className="page-header">
        <h1>{wish.name}</h1>
      </div>
      <Card className="entity-card">
        {owned && (
          <CardActions
            items={[
              { label: t('common.edit'), icon: 'pi pi-pencil', command: () => setDialogOpen(true) },
              { separator: true },
              {
                label: t('common.delete'),
                icon: 'pi pi-trash',
                className: 'menu-item-danger',
                command: remove,
              },
            ]}
          />
        )}
        {wish.description && <p>{wish.description}</p>}
        <Tag
          value={
            wish.quantity === null
              ? t('common.unlimited')
              : isShared(wish)
                ? t('wishes.remaining', { remaining: wish.remaining ?? 0, quantity: wish.quantity })
                : t('wishes.once')
          }
          severity={isShared(wish) && wish.remaining === 0 ? 'warning' : undefined}
        />
        <div className="wish-card-links">
          {wish.links.map((link) => (
            <a key={link.url} href={link.url} target="_blank" rel="noreferrer">
              {link.label || new URL(link.url).hostname}
            </a>
          ))}
        </div>
        {isShared(wish) && <ClaimControls wish={wish} onChanged={reload} />}
      </Card>
      {owned && (
        <WishFormDialog
          visible={dialogOpen}
          wish={wish}
          onSave={save}
          onHide={() => setDialogOpen(false)}
        />
      )}
    </div>
  )
}
