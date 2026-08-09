import axios from 'axios'
import { keycloak } from '../auth/keycloak'
import { ApiError } from './errors'

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL })

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
      throw new ApiError(data.error_code, data.message, error.response.status)
    }
    throw error
  },
)
