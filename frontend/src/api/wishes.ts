import { api } from './client'
import type { ListResponse, OwnWish, PageQuery, SharedWish, UpdatedWish, WishLinkInput } from './types'

export interface WishInput {
  name: string
  description: string | null
  quantity: number | null
  links: WishLinkInput[]
}

/** Owner gets OwnWish items; group members get SharedWish items (with claims). */
export const listWishes = async (
  wishlistId: string,
  page: PageQuery = {},
): Promise<ListResponse<OwnWish | SharedWish>> =>
  (await api.get('/v1/wishes', { params: { wishlist_id: wishlistId, ...page } })).data

/** Owner gets an OwnWish, group members a SharedWish — the backend decides. */
export const getWish = async (id: string): Promise<OwnWish | SharedWish> =>
  (await api.get(`/v1/wishes/${id}`)).data

export const createWish = async (wishlistId: string, input: WishInput): Promise<OwnWish> =>
  (await api.post('/v1/wishes', { wishlist_id: wishlistId, ...input })).data

export const updateWish = async (id: string, input: WishInput): Promise<UpdatedWish> =>
  (await api.patch(`/v1/wishes/${id}`, input)).data

export const deleteWish = async (id: string, confirm = false): Promise<void> => {
  await api.delete(`/v1/wishes/${id}`, { params: confirm ? { confirm: true } : {} })
}
