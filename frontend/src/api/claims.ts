import { api } from './client'
import type { MyClaim, WishClaim } from './types'

export const listMyClaims = async (): Promise<MyClaim[]> => (await api.get('/v1/claims')).data

export const claimWish = async (wishId: string): Promise<WishClaim> =>
  (await api.post('/v1/claims', { wish_id: wishId })).data

export const joinClaim = async (claimId: string): Promise<WishClaim> =>
  (await api.post(`/v1/claims/${claimId}/participants`)).data

export const leaveClaim = async (claimId: string): Promise<void> => {
  await api.delete(`/v1/claims/${claimId}/participants/me`)
}
