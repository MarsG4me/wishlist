import type { ReactNode } from 'react'
import { Skeleton } from 'primereact/skeleton'
import { EmptyState } from './EmptyState'
import { useT } from '../i18n'

/** Page title with optional subtitle on the left and the primary action on the right. */
export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string
  subtitle?: string
  action?: ReactNode
}) {
  return (
    <div className="page-header">
      <div className="page-header-text">
        <h1>{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

/** Placeholder cards shown while a list loads, so the page is never blank. */
export function ListSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="card-grid" aria-busy="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="skeleton-card">
          <Skeleton width="60%" height="1.25rem" />
          <Skeleton width="90%" className="mt-2" />
          <Skeleton width="40%" className="mt-2" />
        </div>
      ))}
    </div>
  )
}

/** Load failure with a retry button. */
export function ErrorState({ onRetry }: { onRetry: () => void }) {
  const t = useT()
  return (
    <EmptyState
      icon="pi pi-exclamation-triangle"
      title={t('state.errorTitle')}
      text={t('state.errorText')}
      action={{ label: t('state.retry'), icon: 'pi pi-refresh', onClick: onRetry }}
    />
  )
}
