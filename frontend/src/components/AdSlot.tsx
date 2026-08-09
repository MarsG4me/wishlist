import { useAuth } from '../auth/AuthProvider'

/**
 * Ad placeholder: renders nothing for the first deploy (VITE_ADS_ENABLED=false).
 * Placed in layout slots now so enabling ads later is a config flip, not a rework.
 * Premium users never see ads.
 */
export function AdSlot({ slot }: { slot: string }) {
  const { user } = useAuth()
  const enabled = import.meta.env.VITE_ADS_ENABLED === 'true'
  if (!enabled || user?.tier !== 'FREE') return null
  return <div className="ad-slot" data-slot={slot} />
}
