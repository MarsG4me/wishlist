import Keycloak from 'keycloak-js'
import { config } from '../config'

export const keycloak = new Keycloak({
  url: config.keycloakUrl,
  realm: config.keycloakRealm,
  clientId: config.keycloakClientId,
})

/** App-admin = Keycloak realm role `admin` (tier management, aggregate stats). */
export const hasAdminRole = (): boolean => {
  const roles = (keycloak.tokenParsed as { realm_access?: { roles?: string[] } } | undefined)
    ?.realm_access?.roles
  return roles?.includes('admin') ?? false
}
