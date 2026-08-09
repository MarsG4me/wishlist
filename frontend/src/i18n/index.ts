import { useSyncExternalStore } from 'react'
import { keycloak } from '../auth/keycloak'
import type { Locale } from '../api/types'
import { errorsDe } from './errors.de'
import { errorsEn } from './errors.en'
import { uiDe } from './ui.de'
import { uiEn, type UiCatalogue, type UiKey } from './ui.en'
import { applyPrimeLocale } from './primeLocale'

const errorCatalogues: Record<string, Record<string, string>> = { de: errorsDe, en: errorsEn }
const uiCatalogues: Record<string, UiCatalogue> = { de: uiDe, en: uiEn }

/** Account locale, set once /v1/users/me answered; null until then. */
let activeLocale: Locale | null = null

/**
 * Components re-render on a language change by subscribing here — a module-level
 * variable alone would leave every already-rendered string in the old language.
 */
const listeners = new Set<() => void>()

const subscribe = (onStoreChange: () => void): (() => void) => {
  listeners.add(onStoreChange)
  return () => {
    listeners.delete(onStoreChange)
  }
}

/** Applies the account locale; called after every load or change of the own user. */
export function setLanguage(locale: Locale): void {
  if (activeLocale === locale) return
  activeLocale = locale
  applyLanguageToDocument(currentLanguage())
  listeners.forEach((notify) => notify())
}

function applyLanguageToDocument(language: string): void {
  document.documentElement.lang = language
  applyPrimeLocale(language)
}

/**
 * The account setting wins. Before it is loaded (login screen, first paint) the
 * browser language and then the Keycloak locale claim serve as fallbacks.
 */
export function currentLanguage(): string {
  if (activeLocale) return activeLocale.toLowerCase()
  const browser = navigator.language?.slice(0, 2)
  if (browser && uiCatalogues[browser]) return browser
  const tokenLocale = (keycloak.tokenParsed as { locale?: string } | undefined)?.locale?.slice(0, 2)
  if (tokenLocale && uiCatalogues[tokenLocale]) return tokenLocale
  return 'en'
}

type Params = Record<string, string | number>

const interpolate = (template: string, params?: Params): string =>
  params
    ? template.replace(/\{(\w+)\}/g, (match, key: string) =>
        key in params ? String(params[key]) : match,
      )
    : template

/** Localizes a UI key; falls back to English, then to the raw key. */
export function t(key: UiKey, params?: Params): string {
  const catalogue = uiCatalogues[currentLanguage()] ?? uiEn
  return interpolate(catalogue[key] ?? uiEn[key] ?? key, params)
}

/** Localizes a backend error_code; falls back to English, then to the raw code. */
export function tError(errorCode: string): string {
  const catalogue = errorCatalogues[currentLanguage()] ?? errorsEn
  return catalogue[errorCode] ?? errorsEn[errorCode] ?? errorCode
}

/** The active language; re-renders the calling component when it changes. */
export function useLanguage(): string {
  return useSyncExternalStore(subscribe, currentLanguage, currentLanguage)
}

/**
 * Translator for components: identical to `t`, but subscribes the component to
 * language changes so its strings are re-evaluated when the locale switches.
 */
export function useT(): typeof t {
  useLanguage()
  return t
}

// the fallback language already decides the first paint, before /v1/users/me answered
applyLanguageToDocument(currentLanguage())
