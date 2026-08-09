import { Button } from 'primereact/button'
import { Chip } from 'primereact/chip'
import type { SharedWish } from '../api/types'
import { claimWish, joinClaim, leaveClaim } from '../api/claims'
import { useAuth } from '../auth/AuthProvider'
import { useApiToast } from '../hooks/useApiToast'

/**
 * Claim controls for one shared wish: claim a free unit, join an existing claim to
 * pool money, or leave an own participation. One participation per user per wish.
 */
export function ClaimControls({ wish, onChanged }: { wish: SharedWish; onChanged: () => void }) {
  const { user } = useAuth()
  const { showError, showSuccess } = useApiToast()

  const myClaim = wish.claims.find((claim) =>
    claim.participants.some((participant) => participant.id === user?.id),
  )
  const canClaimNew = !myClaim && (wish.remaining === null || wish.remaining > 0)

  const run = async (action: () => Promise<unknown>, message: string) => {
    try {
      await action()
      showSuccess(message)
      onChanged()
    } catch (e) {
      showError(e)
    }
  }

  return (
    <div>
      {wish.claims.map((claim) => (
        <div className="claim-row" key={claim.claim_id}>
          {claim.participants.map((participant) => (
            <Chip key={participant.id} label={participant.displayname} icon="pi pi-gift" />
          ))}
          {myClaim?.claim_id === claim.claim_id ? (
            <Button
              label="Leave"
              icon="pi pi-times"
              size="small"
              text
              severity="danger"
              onClick={() => run(() => leaveClaim(claim.claim_id), 'Left claim')}
            />
          ) : (
            !myClaim && (
              <Button
                label="Chip in"
                icon="pi pi-users"
                size="small"
                text
                onClick={() => run(() => joinClaim(claim.claim_id), 'Joined claim')}
              />
            )
          )}
        </div>
      ))}
      {canClaimNew && (
        <div className="claim-row">
          <Button
            label="Claim"
            icon="pi pi-gift"
            size="small"
            onClick={() => run(() => claimWish(wish.id), 'Claimed')}
          />
        </div>
      )}
    </div>
  )
}
