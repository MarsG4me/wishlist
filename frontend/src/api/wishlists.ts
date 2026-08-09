import { api } from './client'
import type { ListResponse, PageQuery, Wishlist } from './types'

export const listWishlists = async (page: PageQuery = {}): Promise<ListResponse<Wishlist>> =>
  (await api.get('/v1/wishlists', { params: page })).data

export const createWishlist = async (name: string): Promise<Wishlist> =>
  (await api.post('/v1/wishlists', { name })).data

export const getWishlist = async (id: string): Promise<Wishlist> =>
  (await api.get(`/v1/wishlists/${id}`)).data

export const renameWishlist = async (id: string, name: string): Promise<Wishlist> =>
  (await api.patch(`/v1/wishlists/${id}`, { name })).data

export const deleteWishlist = async (id: string): Promise<void> => {
  await api.delete(`/v1/wishlists/${id}`)
}
