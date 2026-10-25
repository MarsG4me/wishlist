import { api, rethrowBlobError } from './client'
import type { Locale, Onboarding, OwnUser, SimpleUser, UserTier } from './types'

export const getOwnUser = async (): Promise<OwnUser> => (await api.get('/v1/users/me')).data

export const getOnboarding = async (): Promise<Onboarding> =>
  (await api.get('/v1/users/me/onboarding')).data

export const updateDisplayname = async (displayname: string): Promise<OwnUser> =>
  (await api.patch('/v1/users/me', { displayname })).data

/** Backend applies the change in Keycloak first; refresh the token afterwards. */
export const updateEmail = async (email: string, currentPassword: string): Promise<OwnUser> =>
  (await api.patch('/v1/users/me/email', { email, current_password: currentPassword })).data

/** Interface language; the backend stores it and mirrors it into Keycloak. */
export const updateLocale = async (locale: Locale): Promise<OwnUser> =>
  (await api.patch('/v1/users/me/locale', { locale })).data

/** In-app password change; backend verifies the current password against Keycloak. */
export const updatePassword = async (currentPassword: string, newPassword: string): Promise<void> => {
  await api.patch('/v1/users/me/password', {
    current_password: currentPassword,
    new_password: newPassword,
  })
}

export const deleteOwnAccount = async (): Promise<void> => {
  await api.delete('/v1/users/me')
}

export const getUser = async (id: string): Promise<SimpleUser> =>
  (await api.get(`/v1/users/${id}`)).data

export const setTier = async (id: string, tier: UserTier): Promise<OwnUser> =>
  (await api.patch(`/v1/users/${id}/tier`, { tier })).data

/** DSGVO export: fetches the account data as a blob and saves it as wishlist-export.json. */
export const downloadExport = async (): Promise<void> => {
  const { data } = await api.get<Blob>('/v1/users/me/export', { responseType: 'blob' }).catch(rethrowBlobError)
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = 'wishlist-export.json'
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
