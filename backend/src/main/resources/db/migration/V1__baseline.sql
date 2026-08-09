-- Baseline schema. Conventions:
--  * all ids are UUIDv7 (generated app-side, uuidv7() as DB-side default)
--  * no created_at columns: creation time is encoded in the UUIDv7 id
--  * FK columns are named <full plural table name>_id
--  * main tables soft-delete via deleted_at; sub tables cascade-delete with their parent

CREATE TYPE user_tier AS ENUM ('FREE', 'PREMIUM');

CREATE TABLE users (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    keycloak_id uuid NOT NULL UNIQUE,
    displayname varchar(60) NOT NULL,
    email       varchar(255) NOT NULL,
    tier        user_tier NOT NULL DEFAULT 'FREE',
    deleted_at  timestamptz NULL DEFAULT NULL
);

CREATE TABLE wishlists (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    users_id   uuid NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    name       varchar(100) NOT NULL,
    deleted_at timestamptz NULL DEFAULT NULL
);
CREATE INDEX idx_wishlists_users ON wishlists (users_id);

CREATE TABLE wishes (
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    wishlists_id uuid NOT NULL REFERENCES wishlists (id) ON UPDATE CASCADE ON DELETE CASCADE,
    name         varchar(100) NOT NULL,
    description  varchar(500) NULL,
    -- NULL = giftable unlimited times
    quantity     integer NULL,
    deleted_at   timestamptz NULL DEFAULT NULL,
    CONSTRAINT chk_wishes_quantity CHECK (quantity IS NULL OR quantity >= 1)
);
CREATE INDEX idx_wishes_wishlists ON wishes (wishlists_id);

CREATE TABLE wish_links (
    id        uuid PRIMARY KEY DEFAULT uuidv7(),
    wishes_id uuid NOT NULL REFERENCES wishes (id) ON UPDATE CASCADE ON DELETE CASCADE,
    url       varchar(2048) NOT NULL,
    label     varchar(100) NULL,
    position  smallint NOT NULL DEFAULT 0
);
CREATE INDEX idx_wish_links_wishes ON wish_links (wishes_id);

CREATE TABLE groups (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    users_id    uuid NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    name        varchar(100) NOT NULL,
    description varchar(500) NULL,
    deleted_at  timestamptz NULL DEFAULT NULL
);
CREATE INDEX idx_groups_users ON groups (users_id);

-- Invites are kept forever as join history: never hard-deleted, never purged.
-- Revocation sets revoked_at.
CREATE TABLE group_invites (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    groups_id  uuid NOT NULL REFERENCES groups (id) ON UPDATE CASCADE ON DELETE CASCADE,
    users_id   uuid NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    code       varchar(32) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NULL DEFAULT NULL,
    -- NULL = unlimited uses within validity window
    max_uses   integer NULL,
    use_count  integer NOT NULL DEFAULT 0,
    CONSTRAINT chk_group_invites_max_uses CHECK (max_uses IS NULL OR max_uses >= 1)
);

CREATE TABLE group_memberships (
    id               uuid PRIMARY KEY DEFAULT uuidv7(),
    groups_id        uuid NOT NULL REFERENCES groups (id) ON UPDATE CASCADE ON DELETE CASCADE,
    users_id         uuid NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    -- which invite was redeemed; NULL only for the owner's auto-membership.
    -- Nullable FK is a documented exception: invites are never deleted, so no dangling risk.
    group_invites_id uuid NULL REFERENCES group_invites (id) ON UPDATE CASCADE ON DELETE CASCADE,
    is_admin         boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_group_memberships UNIQUE (groups_id, users_id)
);
CREATE INDEX idx_group_memberships_users ON group_memberships (users_id);

CREATE TABLE group_wishlist_shares (
    id           uuid PRIMARY KEY DEFAULT uuidv7(),
    groups_id    uuid NOT NULL REFERENCES groups (id) ON UPDATE CASCADE ON DELETE CASCADE,
    wishlists_id uuid NOT NULL REFERENCES wishlists (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_group_wishlist_shares UNIQUE (groups_id, wishlists_id)
);
CREATE INDEX idx_group_wishlist_shares_wishlists ON group_wishlist_shares (wishlists_id);

-- One claim = one gift unit of a wish. Co-claimers pool money as participants of one claim.
-- Claims are hard-deleted (sub-table semantics): a claim vanishes when its last participant leaves.
CREATE TABLE claims (
    id        uuid PRIMARY KEY DEFAULT uuidv7(),
    wishes_id uuid NOT NULL REFERENCES wishes (id) ON UPDATE CASCADE ON DELETE CASCADE,
    users_id  uuid NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    -- composite target so claim_participants can enforce a consistent denormalized wishes_id
    CONSTRAINT uq_claims_id_wishes UNIQUE (id, wishes_id)
);
CREATE INDEX idx_claims_wishes ON claims (wishes_id);

CREATE TABLE claim_participants (
    claims_id uuid NOT NULL,
    -- denormalized to enforce one participation per user per wish across all claims
    wishes_id uuid NOT NULL,
    users_id  uuid NOT NULL REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    PRIMARY KEY (claims_id, users_id),
    CONSTRAINT fk_claim_participants_claims FOREIGN KEY (claims_id, wishes_id)
        REFERENCES claims (id, wishes_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_claim_participants_wish_user UNIQUE (wishes_id, users_id)
);
CREATE INDEX idx_claim_participants_users ON claim_participants (users_id);
