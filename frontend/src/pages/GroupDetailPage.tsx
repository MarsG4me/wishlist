import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { TabView, TabPanel } from 'primereact/tabview'
import { Button } from 'primereact/button'
import { Card } from 'primereact/card'
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
import { ListControls } from '../components/ListControls'
import { InviteDialog } from '../components/InviteDialog'

export function GroupDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { showError, showSuccess } = useApiToast()

  const [group, setGroup] = useState<Group | null>(null)
  const [shareTarget, setShareTarget] = useState<Wishlist | null>(null)
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
  const invites = usePagedList<Invite>(invitesFetcher, showError, 20)

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
      showSuccess('Shared')
      shares.reload()
      loadShareable()
    } catch (e) {
      showError(e)
    }
  }

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
      showSuccess('Saved')
      loadGroup()
    } catch (e) {
      showError(e)
    }
  }

  const removeGroup = () =>
    confirmDialog({
      message: `Delete group "${group.name}"? It can be restored within 30 days.`,
      header: 'Delete group',
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
    if (invite.revoked_at) return <Tag value="revoked" severity="danger" />
    if (new Date(invite.expires_at) < new Date()) return <Tag value="expired" severity="warning" />
    if (invite.max_uses !== null && invite.use_count >= invite.max_uses)
      return <Tag value="exhausted" severity="warning" />
    return <Tag value="active" severity="success" />
  }

  return (
    <div>
      <ConfirmDialog />
      <p>
        <Link to="/groups">← Groups</Link>
      </p>
      <div className="page-header">
        <h1>{group.name}</h1>
      </div>
      <TabView>
        <TabPanel header="Shared wishlists">
          <div className="claim-row" style={{ marginBottom: '1rem' }}>
            <Dropdown
              value={shareTarget}
              options={shareable}
              optionLabel="name"
              placeholder="Share one of my wishlists…"
              emptyMessage="All your wishlists are already shared here"
              onChange={(e) => setShareTarget(e.value)}
            />
            <Button label="Share" icon="pi pi-share-alt" disabled={!shareTarget} onClick={share} />
          </div>
          <div className="card-grid">
            {shares.items.map((share) => (
              <Card key={share.wishlist_id} title={share.name} subTitle={`by ${share.owner.displayname}`}>
                <div className="claim-row">
                  {share.owner.id === user?.id ? (
                    <Button
                      label="Open (my list)"
                      text
                      onClick={() => navigate(`/wishlists/${share.wishlist_id}`)}
                    />
                  ) : (
                    <Button
                      label="Open"
                      icon="pi pi-arrow-right"
                      text
                      onClick={() => navigate(`/groups/${id}/wishlists/${share.wishlist_id}`)}
                    />
                  )}
                  {(share.owner.id === user?.id || canManage) && (
                    <Button
                      icon="pi pi-times"
                      text
                      severity="danger"
                      onClick={async () => {
                        try {
                          await unshareWishlist(id, share.wishlist_id)
                          shares.reload()
                          loadShareable()
                        } catch (e) {
                          showError(e)
                        }
                      }}
                    />
                  )}
                </div>
              </Card>
            ))}
            {shares.total === 0 && !shares.loading && <p className="muted">Nothing shared yet.</p>}
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

        <TabPanel header="Members">
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
          <TabPanel header="Invites">
            <div className="claim-row" style={{ marginBottom: '1rem' }}>
              <label>
                <Checkbox checked={inviteLimited} onChange={(e) => setInviteLimited(!!e.checked)} /> Limit
                uses
              </label>
              {inviteLimited && (
                <InputNumber
                  value={inviteMaxUses}
                  min={1}
                  showButtons
                  onValueChange={(e) => setInviteMaxUses(e.value ?? 1)}
                />
              )}
              <Button label="New invite" icon="pi pi-plus" onClick={newInvite} />
            </div>
            <DataTable value={invites.items} dataKey="id" emptyMessage="No invites yet">
              <Column field="code" header="Code" />
              <Column header="Status" body={inviteState} />
              <Column
                header="Uses"
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
          <TabPanel header="Settings">
            <div className="form-field" style={{ maxWidth: 400 }}>
              <label htmlFor="edit-name">Name</label>
              <InputText id="edit-name" value={editName} maxLength={100} onChange={(e) => setEditName(e.target.value)} />
            </div>
            <div className="form-field" style={{ maxWidth: 400 }}>
              <label htmlFor="edit-desc">Description</label>
              <InputTextarea
                id="edit-desc"
                value={editDescription}
                maxLength={500}
                rows={3}
                onChange={(e) => setEditDescription(e.target.value)}
              />
            </div>
            <div className="claim-row">
              <Button label="Save" icon="pi pi-check" disabled={!editName.trim()} onClick={saveSettings} />
              <Button label="Delete group" icon="pi pi-trash" severity="danger" outlined onClick={removeGroup} />
            </div>
          </TabPanel>
        )}
      </TabView>
      <InviteDialog invite={freshInvite} onHide={() => setFreshInvite(null)} />
    </div>
  )
}
