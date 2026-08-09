import { api } from './client'
import type { OwnWish, SharedWish, UpdatedWish, WishLink } from './types'

export interface WishInput {
  name: string
  description: string | null
  quantity: number | null
  links: WishLink[]
}

/** Owner gets OwnWish[]; group members get SharedWish[] (with claims). */
export const listWishes = async (wishlistId: string): Promise<(OwnWish | SharedWish)[]> =>
  (await api.get('/v1/wishes', { params: { wishlist_id: wishlistId } })).data

export const createWish = async (wishlistId: string, input: WishInput): Promise<OwnWish> =>
  (await api.post('/v1/wishes', { wishlist_id: wishlistId, ...input })).data

export const updateWish = async (id: string, input: WishInput): Promise<UpdatedWish> =>
  (await api.patch(`/v1/wishes/${id}`, input)).data

export const deleteWish = async (id: string, confirm = false): Promise<void> => {
  await api.delete(`/v1/wishes/${id}`, { params: confirm ? { confirm: true } : {} })
}
