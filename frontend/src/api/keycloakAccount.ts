import axios from 'axios'
import { keycloak } from '../auth/keycloak'

/**
 * Direct calls to the Keycloak Account API with the user's own token.
 * Password and language are Keycloak-only concerns — our backend is never involved.
 * (Email changes go through OUR backend so the DB copy stays in sync.)
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

/** Sends the user to Keycloak's hosted account console for password changes. */
export const openPasswordManagement = (): void => {
  window.location.href = `${accountUrl()}/account-security/signing-in`
}
