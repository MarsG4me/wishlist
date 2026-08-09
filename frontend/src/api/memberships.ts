import { api } from './client'
import type { ListResponse, Membership, PageQuery } from './types'

export const joinGroup = async (code: string): Promise<Membership> =>
  (await api.post('/v1/memberships/join', { code })).data

export const listMembers = async (
  groupId: string,
  page: PageQuery = {},
): Promise<ListResponse<Membership>> =>
  (await api.get('/v1/memberships', { params: { group_id: groupId, ...page } })).data

export const setMemberAdmin = async (membershipId: string, isAdmin: boolean): Promise<Membership> =>
  (await api.patch(`/v1/memberships/${membershipId}`, { is_admin: isAdmin })).data

export const removeMember = async (membershipId: string): Promise<void> => {
  await api.delete(`/v1/memberships/${membershipId}`)
}
