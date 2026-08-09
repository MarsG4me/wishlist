import { api } from './client'
import type { Group, Invite, ListResponse, PageQuery, SharedWishlist, Wishlist } from './types'

export const listGroups = async (page: PageQuery = {}): Promise<ListResponse<Group>> =>
  (await api.get('/v1/groups', { params: page })).data

export const createGroup = async (name: string, description: string | null): Promise<Group> =>
  (await api.post('/v1/groups', { name, description })).data

export const getGroup = async (id: string): Promise<Group> => (await api.get(`/v1/groups/${id}`)).data

export const updateGroup = async (id: string, name: string, description: string | null): Promise<Group> =>
  (await api.patch(`/v1/groups/${id}`, { name, description })).data

export const deleteGroup = async (id: string): Promise<void> => {
  await api.delete(`/v1/groups/${id}`)
}

export const listShares = async (
  groupId: string,
  page: PageQuery = {},
): Promise<ListResponse<SharedWishlist>> =>
  (await api.get(`/v1/groups/${groupId}/shares`, { params: page })).data

/** Own wishlists not yet shared here — the backend filters, so paging stays correct. */
export const listShareableWishlists = async (
  groupId: string,
  page: PageQuery = {},
): Promise<ListResponse<Wishlist>> =>
  (await api.get(`/v1/groups/${groupId}/shareable-wishlists`, { params: page })).data

export const shareWishlist = async (groupId: string, wishlistId: string): Promise<void> => {
  await api.post(`/v1/groups/${groupId}/shares`, { wishlist_id: wishlistId })
}

export const unshareWishlist = async (groupId: string, wishlistId: string): Promise<void> => {
  await api.delete(`/v1/groups/${groupId}/shares/${wishlistId}`)
}

export const listInvites = async (
  groupId: string,
  page: PageQuery = {},
): Promise<ListResponse<Invite>> =>
  (await api.get(`/v1/groups/${groupId}/invites`, { params: page })).data

export const createInvite = async (groupId: string, maxUses: number | null): Promise<Invite> =>
  (await api.post(`/v1/groups/${groupId}/invites`, { max_uses: maxUses })).data

export const revokeInvite = async (groupId: string, inviteId: string): Promise<void> => {
  await api.delete(`/v1/groups/${groupId}/invites/${inviteId}`)
}
