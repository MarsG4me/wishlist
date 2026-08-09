import { useRef } from 'react'
import { Button } from 'primereact/button'
import { Menu } from 'primereact/menu'
import type { MenuItem } from 'primereact/menuitem'
import { useT } from '../i18n'

/**
 * Edit affordance of an entity card: a pencil in the top-right corner opening a popup
 * menu. Destructive actions live one level deep in that menu, never as a bare icon
 * next to the card link — the card itself is the way to open the entity.
 */
export function CardActions({ items, label }: { items: MenuItem[]; label?: string }) {
  const menu = useRef<Menu>(null)
  const t = useT()

  if (items.length === 0) return null
  return (
    <div className="entity-card-actions">
      <Menu model={items} popup ref={menu} />
      <Button
        icon="pi pi-pencil"
        text
        rounded
        aria-label={label ?? t('common.edit')}
        onClick={(e) => menu.current?.toggle(e)}
      />
    </div>
  )
}
