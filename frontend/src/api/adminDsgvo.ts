import { api, rethrowBlobError } from './client'
import type { AdminUser } from './admin'
import type { ListResponse, PageQuery } from './types'

export interface AdminUserDeletable extends AdminUser {
  deleted_at: string | null
}

/** Admin user search that can also return deleted accounts. */
export const searchAdminUsers = async (
  email: string,
  includeDeleted: boolean,
  page: PageQuery = {},
): Promise<ListResponse<AdminUserDeletable>> =>
  (await api.get('/v1/admin/users', { params: { email, include_deleted: includeDeleted, ...page } })).data

/** Fetches the user's data export with the bearer token and saves it as a JSON file. */
export const downloadUserExport = async (userId: string): Promise<void> => {
  const response = await api
    .get(`/v1/admin/users/${userId}/export`, { responseType: 'blob' })
    .catch(rethrowBlobError)
  const url = URL.createObjectURL(new Blob([response.data], { type: 'application/json' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `user-export-${userId}.json`
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** immediate=false starts the grace period, true erases the account right away. */
export const deleteAdminUser = async (userId: string, immediate: boolean): Promise<void> => {
  await api.delete(`/v1/admin/users/${userId}`, { params: { immediate } })
}
