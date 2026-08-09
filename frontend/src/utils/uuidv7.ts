/**
 * Mirrors the backend's UuidV7Factory.timestampOf: entities carry no created_at
 * column — creation time is embedded in the UUIDv7 id.
 */
export function timestampOf(uuidV7: string): Date {
  const hex = uuidV7.replace(/-/g, '').slice(0, 12)
  return new Date(Number.parseInt(hex, 16))
}
