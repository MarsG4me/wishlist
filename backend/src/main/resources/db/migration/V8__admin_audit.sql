-- Who did what to which account. No FK on admin_users_id/target_id: rows must outlive
-- purged accounts. The age comes from the UUIDv7 id; the purge job drops rows after a year.
-- More actions are added later with ALTER TYPE admin_audit_action ADD VALUE.
CREATE TYPE admin_audit_action AS ENUM (
    'USER_SEARCH', 'USER_EXPORT', 'USER_DELETE', 'USER_DELETE_IMMEDIATE', 'TIER_CHANGE');

CREATE TABLE admin_audit (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    admin_users_id uuid NOT NULL,
    action         admin_audit_action NOT NULL,
    target_id      uuid NULL,
    detail         jsonb NULL
);
