import axios from 'axios'
import { api } from './client'
import { ApiError } from './errors'
import { keycloak } from '../auth/keycloak'
import { config } from '../config'
import type { InvitePreview, ListResponse, Membership, PageQuery } from './types'

/** Public invite preview; sends the token only when logged in, never forces a login. */
export const previewInvite = async (code: string): Promise<InvitePreview> => {
  try {
    const fresh = await keycloak.updateToken(30).then(() => true, () => false)
    const headers = fresh && keycloak.token ? { Authorization: `Bearer ${keycloak.token}` } : {}
    const res = await axios.get(`${config.apiUrl}/v1/invites/${encodeURIComponent(code)}/preview`, { headers })
    return res.data
  } catch (error) {
    const data = axios.isAxiosError(error) ? error.response?.data : undefined
    if (data?.error_code) throw new ApiError(data.error_code, data.message, axios.isAxiosError(error) ? (error.response?.status ?? 0) : 0)
    throw error
  }
}

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
