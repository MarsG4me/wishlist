-- A purged account must not take shared rows with it: invites it created, the
-- memberships that redeemed them and groups it created outlive the creator.
ALTER TABLE group_invites ALTER COLUMN users_id DROP NOT NULL;
ALTER TABLE group_invites DROP CONSTRAINT group_invites_users_id_fkey;
ALTER TABLE group_invites ADD CONSTRAINT group_invites_users_id_fkey
    FOREIGN KEY (users_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE group_memberships DROP CONSTRAINT group_memberships_group_invites_id_fkey;
ALTER TABLE group_memberships ADD CONSTRAINT group_memberships_group_invites_id_fkey
    FOREIGN KEY (group_invites_id) REFERENCES group_invites (id) ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE groups ALTER COLUMN users_id DROP NOT NULL;
ALTER TABLE groups DROP CONSTRAINT groups_users_id_fkey;
ALTER TABLE groups ADD CONSTRAINT groups_users_id_fkey
    FOREIGN KEY (users_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL;
