import { useCallback, useState } from 'react'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { InputText } from 'primereact/inputtext'
import { InputTextarea } from 'primereact/inputtextarea'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import { Link } from 'react-router-dom'
import { createGroup, deleteGroup, listGroups, updateGroup } from '../api/groups'
import type { Group, PageQuery } from '../api/types'
import { CardActions } from '../components/CardActions'
import { ListControls } from '../components/ListControls'
import { useApiToast } from '../hooks/useApiToast'
import { useListStatus } from '../hooks/useListStatus'
import { EmptyState } from '../components/EmptyState'
import { ErrorState, ListSkeleton, PageHeader } from '../components/PageStates'
import { useT } from '../i18n'

export function GroupsPage() {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<Group | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  const fetcher = useCallback((page: PageQuery) => listGroups(page), [])
  const list = useListStatus<Group>(fetcher, showError)

  const openCreate = () => {
    setEditing(null)
    setName('')
    setDescription('')
    setDialogOpen(true)
  }

  const openEdit = (group: Group) => {
    setEditing(group)
    setName(group.name)
    setDescription(group.description ?? '')
    setDialogOpen(true)
  }

  const save = async () => {
    try {
      if (editing) await updateGroup(editing.id, name, description || null)
      else await createGroup(name, description || null)
      setDialogOpen(false)
      showSuccess(editing ? t('common.saved') : t('groups.created'))
      list.reload()
    } catch (e) {
      showError(e)
    }
  }

  const remove = (group: Group) =>
    confirmDialog({
      message: t('groups.deleteMessage', { name: group.name }),
      header: t('groups.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await deleteGroup(group.id)
          list.reload()
        } catch (e) {
          showError(e)
        }
      },
    })

  return (
    <div>
      <ConfirmDialog />
      <PageHeader
        title={t('groups.title')}
        subtitle={t('groups.subtitle')}
        action={<Button label={t('groups.new')} icon="pi pi-plus" onClick={openCreate} />}
      />
      {list.failed ? (
        <ErrorState onRetry={list.reload} />
      ) : list.initial ? (
        <ListSkeleton />
      ) : list.total === 0 ? (
        <EmptyState
          icon="pi pi-users"
          title={t('empty.groups.title')}
          text={t('empty.groups.text')}
          action={{ label: t('groups.new'), icon: 'pi pi-plus', onClick: openCreate }}
        >
          <p className="empty-state-hint">
            <i className="pi pi-link" /> {t('empty.groups.join')}
          </p>
        </EmptyState>
      ) : (
      <div className="card-grid">
        {list.items.map((group) => (
          <Card
            key={group.id}
            className="entity-card"
            title={<Link to={`/groups/${group.id}`}>{group.name}</Link>}
            subTitle={group.description ?? undefined}
          >
            {/* admin-only, and the permission comes from the group itself, never from a member scan */}
            <CardActions
              items={
                group.is_admin
                  ? [
                      { label: t('common.edit'), icon: 'pi pi-pencil', command: () => openEdit(group) },
                      { separator: true },
                      {
                        label: t('common.delete'),
                        icon: 'pi pi-trash',
                        className: 'menu-item-danger',
                        command: () => remove(group),
                      },
                    ]
                  : []
              }
            />
          </Card>
        ))}
      </div>
      )}
      <ListControls
        total={list.total}
        limit={list.limit}
        offset={list.offset}
        loading={list.loading}
        isMobile={list.isMobile}
        hasMore={list.hasMore}
        onLimit={list.setLimit}
        onOffset={list.setOffset}
        onLoadMore={list.loadMore}
      />
      <Dialog
        header={editing ? t('groups.editHeader') : t('groups.newHeader')}
        visible={dialogOpen}
        style={{ width: 'min(400px, 95vw)' }}
        onHide={() => setDialogOpen(false)}
      >
        <div className="form-field">
          <label htmlFor="group-name">{t('common.name')}</label>
          <InputText id="group-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="form-field">
          <label htmlFor="group-desc">{t('common.description')}</label>
          <InputTextarea
            id="group-desc"
            value={description}
            maxLength={500}
            rows={3}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <Button
          label={editing ? t('common.save') : t('common.create')}
          icon="pi pi-check"
          disabled={!name.trim()}
          onClick={save}
        />
      </Dialog>
    </div>
  )
}
