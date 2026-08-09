-- Permanently removes soft-deleted rows older than 30 days.
-- FK cascades clean up all sub tables (wish_links, memberships, shares, claims, participants).
-- group_invites are intentionally NEVER purged: they are join history.
-- Run manually or via cron: psql "$DB_URL" -f purge_soft_deleted.sql

BEGIN;

DELETE FROM wishes    WHERE deleted_at < now() - interval '30 days';
DELETE FROM wishlists WHERE deleted_at < now() - interval '30 days';
DELETE FROM groups    WHERE deleted_at < now() - interval '30 days';
DELETE FROM users     WHERE deleted_at < now() - interval '30 days';

COMMIT;
