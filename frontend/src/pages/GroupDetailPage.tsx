import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { TabView, TabPanel } from 'primereact/tabview'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
import { Dialog } from 'primereact/dialog'
import { Dropdown } from 'primereact/dropdown'
import { InputText } from 'primereact/inputtext'
import { InputTextarea } from 'primereact/inputtextarea'
import { InputNumber } from 'primereact/inputnumber'
import { Checkbox } from 'primereact/checkbox'
import { DataTable } from 'primereact/datatable'
import { Column } from 'primereact/column'
import { Tag } from 'primereact/tag'
import { confirmDialog, ConfirmDialog } from 'primereact/confirmdialog'
import {
  createInvite,
  deleteGroup,
  getGroup,
  listInvites,
  listShareableWishlists,
  listShares,
  revokeInvite,
  shareWishlist,
  unshareWishlist,
  updateGroup,
} from '../api/groups'
import { listMembers, removeMember, setMemberAdmin } from '../api/memberships'
import type { Group, Invite, Membership, PageQuery, SharedWishlist, Wishlist } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { useApiToast } from '../hooks/useApiToast'
import { usePagedList } from '../hooks/usePagedList'
import { MemberTable } from '../components/MemberTable'
import { CardActions } from '../components/CardActions'
import { ListControls } from '../components/ListControls'
import { InviteDialog } from '../components/InviteDialog'
import { useT } from '../i18n'

export function GroupDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { showError, showSuccess } = useApiToast()
  const t = useT()

  const [group, setGroup] = useState<Group | null>(null)
  const [shareTarget, setShareTarget] = useState<Wishlist | null>(null)
  const [shareDialogOpen, setShareDialogOpen] = useState(false)
  const [shareable, setShareable] = useState<Wishlist[]>([])
  const [freshInvite, setFreshInvite] = useState<Invite | null>(null)
  const [inviteLimited, setInviteLimited] = useState(false)
  const [inviteMaxUses, setInviteMaxUses] = useState(5)
  const [editName, setEditName] = useState('')
  const [editDescription, setEditDescription] = useState('')

  // permission comes from the group endpoint (boolean query), not from the member list
  const canManage = group?.is_admin ?? false

  const sharesFetcher = useCallback((page: PageQuery) => listShares(id!, page), [id])
  const membersFetcher = useCallback((page: PageQuery) => listMembers(id!, page), [id])
  const invitesFetcher = useCallback((page: PageQuery) => listInvites(id!, page), [id])
  const shares = usePagedList<SharedWishlist>(sharesFetcher, showError)
  const members = usePagedList<Membership>(membersFetcher, showError)
  // invites are admin-only: fetching them as a plain member would only produce a 403 toast
  const invites = usePagedList<Invite>(invitesFetcher, showError, 20, canManage)

  const loadGroup = useCallback(() => {
    if (!id) return
    getGroup(id)
      .then((g) => {
        setGroup(g)
        setEditName(g.name)
        setEditDescription(g.description ?? '')
      })
      .catch(showError)
  }, [id])

  /** The share dropdown is server-filtered, so it stays correct however shares are paged. */
  const loadShareable = useCallback(() => {
    if (!id) return
    listShareableWishlists(id, { limit: 100 })
      .then((page) => setShareable(page.items))
      .catch(showError)
  }, [id])

  useEffect(() => {
    loadGroup()
    loadShareable()
  }, [loadGroup, loadShareable])

  if (!id || !group) return <p className="muted">…</p>

  const share = async () => {
    if (!shareTarget) return
    try {
      await shareWishlist(id, shareTarget.id)
      setShareTarget(null)
      setShareDialogOpen(false)
      showSuccess(t('groups.shared'))
      shares.reload()
      loadShareable()
    } catch (e) {
      showError(e)
    }
  }

  const unshare = (share: SharedWishlist) =>
    confirmDialog({
      message: t('groups.unshareMessage', { name: share.name }),
      header: t('groups.unshareHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await unshareWishlist(id, share.wishlist_id)
          shares.reload()
          loadShareable()
        } catch (e) {
          showError(e)
        }
      },
    })

  const newInvite = async () => {
    try {
      const invite = await createInvite(id, inviteLimited ? inviteMaxUses : null)
      invites.reload()
      setFreshInvite(invite)
    } catch (e) {
      showError(e)
    }
  }

  const saveSettings = async () => {
    try {
      await updateGroup(id, editName, editDescription || null)
      showSuccess(t('common.saved'))
      loadGroup()
    } catch (e) {
      showError(e)
    }
  }

  const removeGroup = () =>
    confirmDialog({
      message: t('groups.deleteMessage', { name: group.name }),
      header: t('groups.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptClassName: 'p-button-danger',
      accept: async () => {
        try {
          await deleteGroup(id)
          navigate('/groups')
        } catch (e) {
          showError(e)
        }
      },
    })

  const inviteState = (invite: Invite) => {
    if (invite.revoked_at) return <Tag value={t('invites.stateRevoked')} severity="danger" />
    if (new Date(invite.expires_at) < new Date())
      return <Tag value={t('invites.stateExpired')} severity="warning" />
    if (invite.max_uses !== null && invite.use_count >= invite.max_uses)
      return <Tag value={t('invites.stateExhausted')} severity="warning" />
    return <Tag value={t('invites.stateActive')} severity="success" />
  }

  return (
    <div>
      <ConfirmDialog />
      <p>
        <Link to="/groups">{t('groups.backLink')}</Link>
      </p>
      <div className="page-header">
        <h1>{group.name}</h1>
      </div>
      <TabView>
        <TabPanel header={t('groups.tabShares')}>
          <div className="claim-row" style={{ marginBottom: '1rem' }}>
            <Button
              label={t('groups.shareWishlist')}
              icon="pi pi-share-alt"
              onClick={() => setShareDialogOpen(true)}
            />
          </div>
          <div className="card-grid">
            {shares.items.map((entry) => {
              // an own list stays the editable own view; other people's lists open the claim view
              const own = entry.owner.id === user?.id
              const target = own
                ? `/wishlists/${entry.wishlist_id}`
                : `/groups/${id}/wishlists/${entry.wishlist_id}`
              return (
                <Card
                  key={entry.wishlist_id}
                  className="entity-card"
                  title={<Link to={target}>{entry.name}</Link>}
                  subTitle={
                    own ? t('groups.myList') : t('groups.byOwner', { name: entry.owner.displayname })
                  }
                >
                  <CardActions
                    label={t('groups.manageShare')}
                    items={
                      own || canManage
                        ? [
                            {
                              label: t('groups.removeFromGroup'),
                              icon: 'pi pi-times',
                              className: 'menu-item-danger',
                              command: () => unshare(entry),
                            },
                          ]
                        : []
                    }
                  />
                </Card>
              )
            })}
            {shares.total === 0 && !shares.loading && (
              <p className="muted">{t('groups.nothingShared')}</p>
            )}
          </div>
          <ListControls
            total={shares.total}
            limit={shares.limit}
            offset={shares.offset}
            loading={shares.loading}
            isMobile={shares.isMobile}
            hasMore={shares.hasMore}
            onLimit={shares.setLimit}
            onOffset={shares.setOffset}
            onLoadMore={shares.loadMore}
          />
        </TabPanel>

        <TabPanel header={t('groups.tabMembers')}>
          <MemberTable
            members={members.items}
            ownUserId={user?.id ?? ''}
            canManage={canManage}
            onToggleAdmin={async (membership, isAdmin) => {
              try {
                await setMemberAdmin(membership.id, isAdmin)
                members.reload()
                loadGroup()
              } catch (e) {
                showError(e)
              }
            }}
            onRemove={async (membership) => {
              try {
                await removeMember(membership.id)
                if (membership.user.id === user?.id) navigate('/groups')
                else members.reload()
              } catch (e) {
                showError(e)
              }
            }}
          />
          <ListControls
            total={members.total}
            limit={members.limit}
            offset={members.offset}
            loading={members.loading}
            isMobile={members.isMobile}
            hasMore={members.hasMore}
            onLimit={members.setLimit}
            onOffset={members.setOffset}
            onLoadMore={members.loadMore}
          />
        </TabPanel>

        {canManage && (
          <TabPanel header={t('groups.tabInvites')}>
            <div className="claim-row" style={{ marginBottom: '1rem' }}>
              <label>
                <Checkbox checked={inviteLimited} onChange={(e) => setInviteLimited(!!e.checked)} />{' '}
                {t('invites.limitUses')}
              </label>
              {inviteLimited && (
                <InputNumber
                  value={inviteMaxUses}
                  min={1}
                  showButtons
                  onValueChange={(e) => setInviteMaxUses(e.value ?? 1)}
                />
              )}
              <Button label={t('invites.new')} icon="pi pi-plus" onClick={newInvite} />
            </div>
            <DataTable value={invites.items} dataKey="id" emptyMessage={t('invites.empty')}>
              <Column field="code" header={t('invites.code')} />
              <Column header={t('invites.status')} body={inviteState} />
              <Column
                header={t('invites.uses')}
                body={(invite: Invite) => `${invite.use_count}${invite.max_uses ? ` / ${invite.max_uses}` : ''}`}
              />
              <Column
                header=""
                body={(invite: Invite) =>
                  !invite.revoked_at && (
                    <div className="claim-row">
                      <Button icon="pi pi-qrcode" text onClick={() => setFreshInvite(invite)} />
                      <Button
                        icon="pi pi-ban"
                        text
                        severity="danger"
                        onClick={async () => {
                          try {
                            await revokeInvite(id, invite.id)
                            invites.reload()
                          } catch (e) {
                            showError(e)
                          }
                        }}
                      />
                    </div>
                  )
                }
              />
            </DataTable>
            <ListControls
              total={invites.total}
              limit={invites.limit}
              offset={invites.offset}
              loading={invites.loading}
              isMobile={invites.isMobile}
              hasMore={invites.hasMore}
              onLimit={invites.setLimit}
              onOffset={invites.setOffset}
              onLoadMore={invites.loadMore}
            />
          </TabPanel>
        )}

        {canManage && (
          <TabPanel header={t('groups.tabSettings')}>
            <div className="form-field" style={{ maxWidth: 400 }}>
              <label htmlFor="edit-name">{t('common.name')}</label>
              <InputText id="edit-name" value={editName} maxLength={100} onChange={(e) => setEditName(e.target.value)} />
            </div>
            <div className="form-field" style={{ maxWidth: 400 }}>
              <label htmlFor="edit-desc">{t('common.description')}</label>
              <InputTextarea
                id="edit-desc"
                value={editDescription}
                maxLength={500}
                rows={3}
                onChange={(e) => setEditDescription(e.target.value)}
              />
            </div>
            <div className="claim-row">
              <Button
                label={t('common.save')}
                icon="pi pi-check"
                disabled={!editName.trim()}
                onClick={saveSettings}
              />
              <Button
                label={t('groups.delete')}
                icon="pi pi-trash"
                severity="danger"
                outlined
                onClick={removeGroup}
              />
            </div>
          </TabPanel>
        )}
      </TabView>
      <Dialog
        header={t('groups.shareWishlist')}
        visible={shareDialogOpen}
        style={{ width: 'min(400px, 95vw)' }}
        onHide={() => setShareDialogOpen(false)}
      >
        <div className="form-field">
          <label htmlFor="share-target">{t('groups.shareDialogLabel')}</label>
          <Dropdown
            inputId="share-target"
            value={shareTarget}
            options={shareable}
            optionLabel="name"
            placeholder={t('groups.sharePlaceholder')}
            emptyMessage={t('groups.shareEmpty')}
            onChange={(e) => setShareTarget(e.value)}
          />
        </div>
        <Button label={t('common.add')} icon="pi pi-plus" disabled={!shareTarget} onClick={share} />
      </Dialog>
      <InviteDialog invite={freshInvite} onHide={() => setFreshInvite(null)} />
    </div>
  )
}
