import Keycloak from 'keycloak-js'

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
})

/** App-admin = Keycloak realm role `admin` (tier management, aggregate stats). */
export const hasAdminRole = (): boolean => {
  const roles = (keycloak.tokenParsed as { realm_access?: { roles?: string[] } } | undefined)
    ?.realm_access?.roles
  return roles?.includes('admin') ?? false
}
