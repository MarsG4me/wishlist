import { useEffect, useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { InputTextarea } from 'primereact/inputtextarea'
import { useNavigate } from 'react-router-dom'
import { createGroup, listGroups } from '../api/groups'
import type { Group } from '../api/types'
import { useApiToast } from '../hooks/useApiToast'

export function GroupsPage() {
  const [groups, setGroups] = useState<Group[]>([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const navigate = useNavigate()
  const { showError, showSuccess } = useApiToast()

  const reload = () => listGroups().then(setGroups).catch(showError)
  useEffect(() => {
    reload()
  }, [])

  const save = async () => {
    try {
      await createGroup(name, description || null)
      setDialogOpen(false)
      setName('')
      setDescription('')
      showSuccess('Group created')
      reload()
    } catch (e) {
      showError(e)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Groups</h1>
        <Button label="New group" icon="pi pi-plus" onClick={() => setDialogOpen(true)} />
      </div>
      <div className="card-grid">
        {groups.map((group) => (
          <Card key={group.id} title={group.name} subTitle={group.description ?? undefined}>
            <Button
              label="Open"
              icon="pi pi-arrow-right"
              text
              onClick={() => navigate(`/groups/${group.id}`)}
            />
          </Card>
        ))}
        {groups.length === 0 && (
          <p className="muted">No groups yet — create one or join via an invite link.</p>
        )}
      </div>
      <Dialog
        header="New group"
        visible={dialogOpen}
        style={{ width: 'min(400px, 95vw)' }}
        onHide={() => setDialogOpen(false)}
      >
        <div className="form-field">
          <label htmlFor="group-name">Name</label>
          <InputText id="group-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="form-field">
          <label htmlFor="group-desc">Description</label>
          <InputTextarea
            id="group-desc"
            value={description}
            maxLength={500}
            rows={3}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <Button label="Create" icon="pi pi-check" disabled={!name.trim()} onClick={save} />
      </Dialog>
    </div>
  )
}
