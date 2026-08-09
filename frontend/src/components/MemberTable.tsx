import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { InputSwitch } from 'primereact/inputswitch'
import { Tag } from 'primereact/tag'
import type { Membership } from '../api/types'
import { useT } from '../i18n'

interface Props {
  members: Membership[]
  ownUserId: string
  canManage: boolean
  onToggleAdmin: (membership: Membership, isAdmin: boolean) => void
  onRemove: (membership: Membership) => void
}

/**
 * Groups can have several admins; the backend blocks demoting or removing the LAST
 * admin, so all rows stay editable here ("Owner" tag is informational: the creator).
 */
export function MemberTable({ members, ownUserId, canManage, onToggleAdmin, onRemove }: Props) {
  const t = useT()

  return (
    <DataTable value={members} dataKey="id" emptyMessage={t('members.empty')}>
      <Column field="user.displayname" header={t('members.member')} />
      <Column
        header={t('members.role')}
        body={(m: Membership) => (
          <span className="claim-row">
            {m.is_owner && <Tag value={t('members.owner')} />}
            {m.is_admin && <Tag value={t('members.admin')} severity="info" />}
          </span>
        )}
      />
      {canManage && (
        <Column
          header={t('members.admin')}
          body={(m: Membership) => (
            <InputSwitch checked={m.is_admin} onChange={(e) => onToggleAdmin(m, !!e.value)} />
          )}
        />
      )}
      <Column
        header=""
        body={(m: Membership) => {
          const isSelf = m.user.id === ownUserId
          if (!canManage && !isSelf) return null
          return (
            <Button
              icon={isSelf ? 'pi pi-sign-out' : 'pi pi-user-minus'}
              label={isSelf ? t('common.leave') : undefined}
              severity="danger"
              text
              onClick={() => onRemove(m)}
            />
          )
        }}
      />
    </DataTable>
  )
}
