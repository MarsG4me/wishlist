import { addLocale, locale as setPrimeLocale } from 'primereact/api'

/**
 * PrimeReact ships its own strings (confirm dialog buttons, paginator aria labels,
 * dropdown empty messages). They are keyed by locale name, so the app locale has to
 * be handed over to PrimeReact as well — otherwise a German UI still confirms with "Yes".
 * Only the keys this app can actually surface are translated.
 */
addLocale('de', {
  accept: 'Ja',
  reject: 'Nein',
  cancel: 'Abbrechen',
  choose: 'Auswählen',
  clear: 'Leeren',
  emptyMessage: 'Keine Einträge gefunden',
  emptyFilterMessage: 'Keine Ergebnisse gefunden',
  aria: {
    close: 'Schließen',
    prevPageLabel: 'Vorherige Seite',
    nextPageLabel: 'Nächste Seite',
    firstPageLabel: 'Erste Seite',
    lastPageLabel: 'Letzte Seite',
    jumpToPageDropdownLabel: 'Zu Seite springen',
    rowsPerPageLabel: 'Einträge pro Seite',
    selectRow: 'Zeile auswählen',
    unselectRow: 'Zeile abwählen',
  },
})

/** Applies a language to PrimeReact's own strings; unknown languages fall back to English. */
export function applyPrimeLocale(language: string): void {
  setPrimeLocale(language === 'de' ? 'de' : 'en')
}
