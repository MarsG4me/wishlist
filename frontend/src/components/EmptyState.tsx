import type { ReactNode } from 'react'
import { Button } from 'primereact/button'

interface Action {
  label: string
  onClick: () => void
  icon?: string
}

interface Props {
  icon: string
  title: string
  text: string
  action?: Action
  secondary?: Action
  children?: ReactNode
}

/** Centered hint card shown instead of an empty list: icon, title, explanation, up to two actions. */
export function EmptyState({ icon, title, text, action, secondary, children }: Props) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">
        <i className={icon} />
      </div>
      <h2>{title}</h2>
      <p>{text}</p>
      {children}
      {(action || secondary) && (
        <div className="empty-state-actions">
          {action && <Button label={action.label} icon={action.icon} onClick={action.onClick} />}
          {secondary && (
            <Button
              label={secondary.label}
              icon={secondary.icon}
              outlined
              onClick={secondary.onClick}
            />
          )}
        </div>
      )}
    </div>
  )
}
