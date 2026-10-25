import axios from 'axios'
import { keycloak } from '../auth/keycloak'
import { config } from '../config'
import { ApiError } from './errors'

export const api = axios.create({ baseURL: config.apiUrl })

api.interceptors.request.use(async (config) => {
  try {
    await keycloak.updateToken(30)
  } catch {
    keycloak.login()
  }
  config.headers.Authorization = `Bearer ${keycloak.token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error.response?.data
    if (data?.error_code) {
      throw new ApiError(data.error_code, data.message, error.response.status, data.link_index ?? undefined)
    }
    throw error
  },
)

/** Rethrows a failed blob request as ApiError by parsing the error Blob's JSON body. */
export const rethrowBlobError = async (error: unknown): Promise<never> => {
  const res = axios.isAxiosError(error) ? error.response : undefined
  if (res?.data instanceof Blob) {
    try {
      const data = JSON.parse(await res.data.text())
      if (data?.error_code) throw new ApiError(data.error_code, data.message, res.status, data.link_index ?? undefined)
    } catch (e) {
      if (e instanceof ApiError) throw e
    }
  }
  throw error
}
