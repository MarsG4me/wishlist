import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Button } from 'primereact/button'
import { InputSwitch } from 'primereact/inputswitch'
import { Tag } from 'primereact/tag'
import type { Membership } from '../api/types'

interface Props {
  members: Membership[]
  ownUserId: string
  canManage: boolean
  onToggleAdmin: (membership: Membership, isAdmin: boolean) => void
  onRemove: (membership: Membership) => void
}

export function MemberTable({ members, ownUserId, canManage, onToggleAdmin, onRemove }: Props) {
  return (
    <DataTable value={members} dataKey="id" emptyMessage="No members">
      <Column field="user.displayname" header="Member" />
      <Column
        header="Role"
        body={(m: Membership) =>
          m.is_owner ? <Tag value="Owner" /> : m.is_admin ? <Tag value="Admin" severity="info" /> : null
        }
      />
      {canManage && (
        <Column
          header="Admin"
          body={(m: Membership) =>
            m.is_owner ? null : (
              <InputSwitch checked={m.is_admin} onChange={(e) => onToggleAdmin(m, !!e.value)} />
            )
          }
        />
      )}
      <Column
        header=""
        body={(m: Membership) => {
          if (m.is_owner) return null
          const isSelf = m.user.id === ownUserId
          if (!canManage && !isSelf) return null
          return (
            <Button
              icon={isSelf ? 'pi pi-sign-out' : 'pi pi-user-minus'}
              label={isSelf ? 'Leave' : undefined}
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
