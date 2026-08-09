import axios from 'axios'
import { keycloak } from '../auth/keycloak'

/**
 * Direct calls to the Keycloak Account API with the user's own token.
 * Only the language preference lives here — email and password changes go through
 * OUR backend (in-app UI, DB stays in sync, no redirect to Keycloak pages).
 */
const accountUrl = () =>
  `${import.meta.env.VITE_KEYCLOAK_URL}/realms/${import.meta.env.VITE_KEYCLOAK_REALM}/account`

const authHeader = async () => {
  await keycloak.updateToken(30)
  return { Authorization: `Bearer ${keycloak.token}` }
}

export const updateLocale = async (locale: string): Promise<void> => {
  const headers = await authHeader()
  const current = (await axios.get(accountUrl(), { headers })).data
  await axios.post(accountUrl(), { ...current, attributes: { ...current.attributes, locale: [locale] } }, { headers })
}
