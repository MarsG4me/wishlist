import { Button } from 'primereact/button'
import { Chip } from 'primereact/chip'
import { Tag } from 'primereact/tag'
import type { SharedWish } from '../api/types'
import { claimWish, joinClaim, leaveClaim, setClaimShared } from '../api/claims'
import { useApiToast } from '../hooks/useApiToast'
import { useT } from '../i18n'

/**
 * Claim controls for one shared wish: claim a free unit, join a claim its creator opened
 * up to pool money, or leave an own participation. Which of those are possible is decided
 * by the backend (can_claim / can_join / is_participant), matching what the endpoints
 * enforce — including that only the claim's creator, never the wish owner, decides
 * whether others may chip in.
 */
export function ClaimControls({ wish, onChanged }: { wish: SharedWish; onChanged: () => void }) {
  const { showError, showSuccess } = useApiToast()
  const t = useT()

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
            <Chip
              key={participant.id}
              label={participant.displayname ?? t('claims.someoneElse')}
              icon="pi pi-gift"
            />
          ))}
          {claim.is_shared && !claim.is_owner && (
            <Tag value={t('claims.openForOthers')} severity="info" />
          )}
          {claim.is_owner && (
            <Button
              label={claim.is_shared ? t('claims.closeForOthers') : t('claims.letOthersChipIn')}
              icon={claim.is_shared ? 'pi pi-lock' : 'pi pi-lock-open'}
              size="small"
              text
              onClick={() =>
                run(
                  () => setClaimShared(claim.claim_id, !claim.is_shared),
                  claim.is_shared ? t('claims.closedToast') : t('claims.openedToast'),
                )
              }
            />
          )}
          {claim.is_participant ? (
            <Button
              label={t('common.leave')}
              icon="pi pi-times"
              size="small"
              text
              severity="danger"
              onClick={() => run(() => leaveClaim(claim.claim_id), t('claims.left'))}
            />
          ) : (
            claim.can_join && (
              <Button
                label={t('claims.chipIn')}
                icon="pi pi-users"
                size="small"
                text
                onClick={() => run(() => joinClaim(claim.claim_id), t('claims.joined'))}
              />
            )
          )}
        </div>
      ))}
      {wish.can_claim && (
        <div className="claim-row">
          <Button
            label={t('claims.claim')}
            icon="pi pi-gift"
            size="small"
            onClick={() => run(() => claimWish(wish.id), t('claims.claimed'))}
          />
        </div>
      )}
    </div>
  )
}
