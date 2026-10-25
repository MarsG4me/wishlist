export type UserTier = 'FREE' | 'PREMIUM'

/** Interface language of an account; matches the backend enum `user_locale`. */
export type Locale = 'DE' | 'EN'

/** Envelope of every list endpoint. */
export interface ListResponse<T> {
  total: number
  count: number
  limit: number
  offset: number
  items: T[]
}

export interface PageQuery {
  limit?: number
  offset?: number
}

export interface OwnUser {
  id: string
  displayname: string
  email: string
  tier: UserTier
  locale: Locale
  /** Set for Google/Apple logins: credentials are managed by that provider, not here. */
  identity_provider: string | null
}

export interface SimpleUser {
  id: string
  displayname: string
}

/**
 * A claimer. Unlike everywhere else the name may be missing: a wishlist can be shared
 * into several groups, and someone gifting from a group we are not in stays anonymous.
 */
export interface ClaimParticipant {
  id: string
  displayname: string | null
}

export interface Wishlist {
  id: string
  user_id: string
  name: string
}

/** Link as sent in create/edit requests. */
export interface WishLinkInput {
  url: string
  label: string | null
}

export type LinkBlockedBy = 'BLOCKLIST' | 'WEB_RISK' | 'SHORTENER' | 'ADMIN' | 'ADMIN_DOMAIN'

/** Link as returned; blocked/blocked_by are present in owner views only. */
export interface WishLink extends WishLinkInput {
  id?: string
  blocked?: boolean
  blocked_by?: LinkBlockedBy | null
}

/** Owner view: structurally free of claim data. */
export interface OwnWish {
  id: string
  wishlist_id: string
  name: string
  description: string | null
  quantity: number | null
  links: WishLink[]
}

export interface WishClaim {
  claim_id: string
  participants: ClaimParticipant[]
  /** Whether the requester is part of this claim (backend-decided). */
  is_participant: boolean
  /** Whether the requester created it and may therefore open or close it. */
  is_owner: boolean
  /** Whether the creator opened the claim for co-claimers. */
  is_shared: boolean
  /** Whether the requester may join right now (backend-decided). */
  can_join: boolean
}

/** Group-member view: includes claim state. */
export interface SharedWish extends OwnWish {
  remaining: number | null
  claims: WishClaim[]
  /** Whether the requester may open a new claim (backend-decided). */
  can_claim: boolean
}

export interface UpdatedWish {
  wish: OwnWish
  existing_claims_affected: boolean
}

export interface Onboarding {
  wishlist_total: number
  wish_total: number
  group_total: number
}

export interface Group {
  id: string
  user_id: string | null
  name: string
  description: string | null
  /** The requester's own permission, decided by the backend — never derived from lists. */
  is_admin: boolean
}

export interface SharedWishlist {
  wishlist_id: string
  name: string
  owner: SimpleUser
}

export interface Invite {
  id: string
  group_id: string
  code: string
  expires_at: string
  revoked_at: string | null
  max_uses: number | null
  use_count: number
}

export interface InvitePreview {
  group_name?: string | null
  creator_displayname?: string | null
  member_count?: number | null
  expires_at?: string | null
  status: 'valid' | 'expired' | 'revoked' | 'exhausted'
  already_member?: boolean | null
  group_id?: string | null
}

export interface Membership {
  id: string
  group_id: string
  user: SimpleUser
  is_admin: boolean
  is_owner: boolean
}

export interface MyClaim {
  claim_id: string
  wish_id: string
  wish_name: string
  wishlist_id: string
  wishlist_name: string
  wishlist_owner: SimpleUser
  participants: ClaimParticipant[]
  is_owner: boolean
  is_shared: boolean
}
