import { api } from './client'
import type { ListResponse, MyClaim, PageQuery, WishClaim } from './types'

export const listMyClaims = async (page: PageQuery = {}): Promise<ListResponse<MyClaim>> =>
  (await api.get('/v1/claims', { params: page })).data

export const claimWish = async (wishId: string): Promise<WishClaim> =>
  (await api.post('/v1/claims', { wish_id: wishId })).data

export const joinClaim = async (claimId: string): Promise<WishClaim> =>
  (await api.post(`/v1/claims/${claimId}/participants`)).data

/** Only the claim creator may do this; it decides whether others can chip in. */
export const setClaimShared = async (claimId: string, shared: boolean): Promise<WishClaim> =>
  (await api.patch(`/v1/claims/${claimId}`, { is_shared: shared })).data

export const leaveClaim = async (claimId: string): Promise<void> => {
  await api.delete(`/v1/claims/${claimId}/participants/me`)
}
