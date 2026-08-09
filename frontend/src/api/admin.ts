import { api } from './client'
import type { UserTier } from './types'

export interface AdminStats {
  user_count: number
  wishlist_count: number
  wish_count: number
}

export interface AdminUser {
  id: string
  email: string
  displayname: string
  tier: UserTier
}

export const getAdminStats = async (): Promise<AdminStats> => (await api.get('/v1/admin/stats')).data

export const searchUsersByEmail = async (email: string): Promise<AdminUser[]> =>
  (await api.get('/v1/admin/users', { params: { email } })).data
