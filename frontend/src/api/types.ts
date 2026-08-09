export type UserTier = 'FREE' | 'PREMIUM'

export interface OwnUser {
  id: string
  displayname: string
  email: string
  tier: UserTier
}

export interface SimpleUser {
  id: string
  displayname: string
}

export interface Wishlist {
  id: string
  user_id: string
  name: string
}

export interface WishLink {
  url: string
  label: string | null
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
  participants: SimpleUser[]
}

/** Group-member view: includes claim state. */
export interface SharedWish extends OwnWish {
  remaining: number | null
  claims: WishClaim[]
}

export interface UpdatedWish {
  wish: OwnWish
  existing_claims_affected: boolean
}

export interface Group {
  id: string
  user_id: string
  name: string
  description: string | null
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
  participants: SimpleUser[]
}
