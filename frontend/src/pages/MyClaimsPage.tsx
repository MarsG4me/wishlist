import { useCallback } from 'react'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { Chip } from 'primereact/chip'
import { listMyClaims, leaveClaim } from '../api/claims'
import type { MyClaim, PageQuery } from '../api/types'
import { ListControls } from '../components/ListControls'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'

export function MyClaimsPage() {
  const { showError, showSuccess } = useApiToast()
  const fetcher = useCallback((page: PageQuery) => listMyClaims(page), [])
  const list = usePagedList<MyClaim>(fetcher, showError)

  return (
    <div>
      <div className="page-header">
        <h1>My claims</h1>
      </div>
      <DataTable value={list.items} dataKey="claim_id" emptyMessage="You have not claimed anything yet.">
        <Column field="wish_name" header="Wish" />
        <Column
          header="Wishlist"
          body={(claim: MyClaim) => `${claim.wishlist_name} (${claim.wishlist_owner.displayname})`}
        />
        <Column
          header="Together with"
          body={(claim: MyClaim) => (
            <div className="claim-row">
              {claim.participants.map((participant) => (
                <Chip key={participant.id} label={participant.displayname} />
              ))}
            </div>
          )}
        />
        <Column
          header=""
          body={(claim: MyClaim) => (
            <Button
              label="Leave"
              icon="pi pi-times"
              text
              severity="danger"
              onClick={async () => {
                try {
                  await leaveClaim(claim.claim_id)
                  showSuccess('Left claim')
                  list.reload()
                } catch (e) {
                  showError(e)
                }
              }}
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
