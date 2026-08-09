import { keycloak } from '../auth/keycloak'
import { errorsDe } from './errors.de'
import { errorsEn } from './errors.en'

const catalogues: Record<string, Record<string, string>> = { de: errorsDe, en: errorsEn }

/** Browser language is preferred; the Keycloak locale claim is only a fallback. */
export function currentLanguage(): string {
  const browser = navigator.language?.slice(0, 2)
  if (browser && catalogues[browser]) return browser
  const tokenLocale = (keycloak.tokenParsed as { locale?: string } | undefined)?.locale?.slice(0, 2)
  if (tokenLocale && catalogues[tokenLocale]) return tokenLocale
  return 'en'
}

/** Localizes a backend error_code; falls back to English, then to the raw code. */
export function tError(errorCode: string): string {
  return catalogues[currentLanguage()][errorCode] ?? errorsEn[errorCode] ?? errorCode
}
