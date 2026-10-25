import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from 'primereact/button'
import { ProgressBar } from 'primereact/progressbar'
import { getOnboarding } from '../../api/users'
import { listWishlists } from '../../api/wishlists'
import type { Onboarding } from '../../api/types'
import { useT } from '../../i18n'

const DISMISS_KEY = 'wishlist.onboarding.dismissed'

const readDismissed = (): boolean => {
  try {
    return localStorage.getItem(DISMISS_KEY) === '1'
  } catch {
    return false
  }
}

interface Props {
  /** `total` of the own wishlist list. */
  wishlistTotal: number
  onCreateWishlist: () => void
}

/**
 * First-run guide: create a wishlist, add a wish, share via a group. Progress comes from
 * the backend onboarding counts; compact and dismissible once a wishlist exists.
 */
export function OnboardingCard({ wishlistTotal, onCreateWishlist }: Props) {
  const t = useT()
  const navigate = useNavigate()
  const [wishlistId, setWishlistId] = useState<string | null>(null)
  const [counts, setCounts] = useState<Onboarding | null>(null)
  const [dismissed, setDismissed] = useState(readDismissed)

  useEffect(() => {
    let cancelled = false
    getOnboarding()
      .then((c) => !cancelled && setCounts(c))
      .catch(() => {})
    if (wishlistTotal === 0) {
      setWishlistId(null)
    } else {
      listWishlists({ limit: 1 })
        .then((page) => !cancelled && setWishlistId(page.items[0]?.id ?? null))
        .catch(() => {})
    }
    return () => {
      cancelled = true
    }
  }, [wishlistTotal])

  const ownWishlists = counts?.wishlist_total ?? wishlistTotal
  const done = [ownWishlists > 0, (counts?.wish_total ?? 0) > 0, (counts?.group_total ?? 0) > 0]
  const doneCount = done.filter(Boolean).length
  const compact = ownWishlists > 0
  if (doneCount === 3 || (compact && dismissed)) return null

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISS_KEY, '1')
    } catch {
      /* storage unavailable: hide for this session only */
    }
    setDismissed(true)
  }

  const steps = [
    {
      title: t('onboarding.step1.title'),
      text: t('onboarding.step1.text'),
      action: { label: t('wishlists.create'), icon: 'pi pi-plus', onClick: onCreateWishlist },
    },
    {
      title: t('onboarding.step2.title'),
      text: t('onboarding.step2.text'),
      action: wishlistId
        ? {
            label: t('onboarding.step2.action'),
            icon: 'pi pi-plus',
            onClick: () => navigate(`/wishlists/${wishlistId}`),
          }
        : null,
    },
    {
      title: t('onboarding.step3.title'),
      text: t('onboarding.step3.text'),
      action: {
        label: t('onboarding.step3.action'),
        icon: 'pi pi-users',
        onClick: () => navigate('/groups'),
      },
    },
  ]
  // the first open step is the one to act on
  const current = done.indexOf(false)

  return (
    <section className={`onboarding${compact ? ' onboarding-compact' : ''}`}>
      <div className="onboarding-head">
        <h2>{compact ? t('onboarding.compactTitle') : t('onboarding.title')}</h2>
        <span className="muted">{t('onboarding.progress', { done: doneCount })}</span>
        {compact && (
          <Button
            icon="pi pi-times"
            text
            rounded
            severity="secondary"
            aria-label={t('onboarding.dismiss')}
            onClick={dismiss}
          />
        )}
      </div>
      <ProgressBar value={Math.round((doneCount / 3) * 100)} showValue={false} style={{ height: 6 }} />
      <ol className="onboarding-steps">
        {steps.map((step, i) => (
          <li key={i} className={done[i] ? 'is-done' : i === current ? 'is-current' : ''}>
            <span className="onboarding-marker">
              {done[i] ? <i className="pi pi-check" /> : i + 1}
            </span>
            <div className="onboarding-step-body">
              <strong>{step.title}</strong>
              {!compact || i === current ? <span className="muted">{step.text}</span> : null}
            </div>
            {i === current && step.action && (
              <Button
                label={step.action.label}
                icon={step.action.icon}
                size="small"
                onClick={step.action.onClick}
              />
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}
