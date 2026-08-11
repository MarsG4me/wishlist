/**
 * Runtime configuration.
 *
 * The deployment image is built once and run against different environments, so the
 * API and Keycloak coordinates cannot be baked into the bundle. The container writes
 * `/config.js` from its environment before nginx starts; `public/config.js` ships an
 * empty object so `npm run dev` falls back to the `VITE_*` vars in `.env.development`.
 */
type RuntimeConfig = {
  apiUrl?: string
  keycloakUrl?: string
  keycloakRealm?: string
  keycloakClientId?: string
  adsEnabled?: boolean
}

const runtime: RuntimeConfig =
  (window as Window & { __WISHLIST_CONFIG__?: RuntimeConfig }).__WISHLIST_CONFIG__ ?? {}

export const config = {
  // empty string = same origin, which is how the image serves it (nginx proxies /v1)
  apiUrl: runtime.apiUrl ?? import.meta.env.VITE_API_URL ?? '',
  keycloakUrl: runtime.keycloakUrl ?? import.meta.env.VITE_KEYCLOAK_URL ?? '',
  keycloakRealm: runtime.keycloakRealm ?? import.meta.env.VITE_KEYCLOAK_REALM ?? '',
  keycloakClientId: runtime.keycloakClientId ?? import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? '',
  adsEnabled: runtime.adsEnabled ?? import.meta.env.VITE_ADS_ENABLED === 'true',
}
