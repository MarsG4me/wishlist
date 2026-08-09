import { api } from './client'
import type { Wishlist } from './types'

export const listWishlists = async (): Promise<Wishlist[]> => (await api.get('/v1/wishlists')).data

export const createWishlist = async (name: string): Promise<Wishlist> =>
  (await api.post('/v1/wishlists', { name })).data

export const getWishlist = async (id: string): Promise<Wishlist> =>
  (await api.get(`/v1/wishlists/${id}`)).data

export const renameWishlist = async (id: string, name: string): Promise<Wishlist> =>
  (await api.patch(`/v1/wishlists/${id}`, { name })).data

export const deleteWishlist = async (id: string): Promise<void> => {
  await api.delete(`/v1/wishlists/${id}`)
}
