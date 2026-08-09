import { useEffect, useState } from 'react'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { Chip } from 'primereact/chip'
import { listMyClaims, leaveClaim } from '../api/claims'
import type { MyClaim } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'

export function MyClaimsPage() {
  const [claims, setClaims] = useState<MyClaim[]>([])
  const { showError, showSuccess } = useApiToast()

  const reload = () => listMyClaims().then(setClaims).catch(showError)
  useEffect(() => {
    reload()
  }, [])

  return (
    <div>
      <div className="page-header">
        <h1>My claims</h1>
      </div>
      <DataTable value={claims} dataKey="claim_id" emptyMessage="You have not claimed anything yet.">
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
                  reload()
                } catch (e) {
                  showError(e)
                }
              }}
            />
          )}
        />
      </DataTable>
    </div>
  )
}
