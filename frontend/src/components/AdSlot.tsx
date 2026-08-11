import { useAuth } from '../auth/AuthProvider'
import { config } from '../config'

/**
 * Ad placeholder: renders nothing for the first deploy (ads disabled).
 * Placed in layout slots now so enabling ads later is a config flip, not a rework.
 * Premium users never see ads.
 */
export function AdSlot({ slot }: { slot: string }) {
  const { user } = useAuth()
  if (!config.adsEnabled || user?.tier !== 'FREE') return null
  return <div className="ad-slot" data-slot={slot} />
}
