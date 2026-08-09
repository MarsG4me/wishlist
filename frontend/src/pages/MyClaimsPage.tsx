import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { Chip } from 'primereact/chip'
import { listMyClaims, leaveClaim, setClaimShared } from '../api/claims'
import type { MyClaim, PageQuery } from '../api/types'
import { ListControls } from '../components/ListControls'
import { useAuth } from '../auth/AuthProvider'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'
import { useT } from '../i18n'

export function MyClaimsPage() {
  const { user } = useAuth()
  const { showError, showSuccess } = useApiToast()
  const t = useT()
  const fetcher = useCallback((page: PageQuery) => listMyClaims(page), [])
  const list = usePagedList<MyClaim>(fetcher, showError)

  const run = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action()
      showSuccess(message)
      list.reload()
    } catch (e) {
      showError(e)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>{t('claims.title')}</h1>
      </div>
      <DataTable value={list.items} dataKey="claim_id" emptyMessage={t('claims.empty')}>
        <Column
          header={t('claims.wish')}
          body={(claim: MyClaim) => <Link to={`/wishes/${claim.wish_id}`}>{claim.wish_name}</Link>}
        />
        <Column
          header={t('claims.wishlist')}
          body={(claim: MyClaim) =>
            t('claims.wishlistWithOwner', {
              name: claim.wishlist_name,
              owner: claim.wishlist_owner.displayname,
            })
          }
        />
        <Column
          header={t('claims.togetherWith')}
          body={(claim: MyClaim) => {
            // "together with" means the others — the requester is in every own claim by definition
            const others = claim.participants.filter((participant) => participant.id !== user?.id)
            if (others.length === 0) return <span className="muted">{t('common.none')}</span>
            return (
              <div className="claim-row">
                {others.map((participant) => (
                  <Chip key={participant.id} label={participant.displayname} />
                ))}
              </div>
            )
          }}
        />
        <Column
          header={t('claims.othersMayChipIn')}
          body={(claim: MyClaim) =>
            claim.is_owner ? (
              <Button
                label={claim.is_shared ? t('claims.open') : t('claims.closed')}
                icon={claim.is_shared ? 'pi pi-lock-open' : 'pi pi-lock'}
                text
                onClick={() =>
                  run(
                    () => setClaimShared(claim.claim_id, !claim.is_shared),
                    claim.is_shared ? t('claims.closedToast') : t('claims.openedToast'),
                  )
                }
              />
            ) : (
              <span className="muted">{t('claims.notYours')}</span>
            )
          }
        />
        <Column
          header=""
          body={(claim: MyClaim) => (
            <Button
              label={t('common.leave')}
              icon="pi pi-times"
              text
              severity="danger"
              onClick={() => run(() => leaveClaim(claim.claim_id), t('claims.left'))}
            />
          )}
        />
      </DataTable>
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
