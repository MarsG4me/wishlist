import { api } from './client'
import type { OwnUser, SimpleUser, UserTier } from './types'

export const getOwnUser = async (): Promise<OwnUser> => (await api.get('/v1/users/me')).data

export const updateDisplayname = async (displayname: string): Promise<OwnUser> =>
  (await api.patch('/v1/users/me', { displayname })).data

/** Backend applies the change in Keycloak first; refresh the token afterwards. */
export const updateEmail = async (email: string): Promise<OwnUser> =>
  (await api.patch('/v1/users/me/email', { email })).data

export const deleteOwnAccount = async (): Promise<void> => {
  await api.delete('/v1/users/me')
}

export const getUser = async (id: string): Promise<SimpleUser> =>
  (await api.get(`/v1/users/${id}`)).data

export const setTier = async (id: string, tier: UserTier): Promise<OwnUser> =>
  (await api.patch(`/v1/users/${id}/tier`, { tier })).data
