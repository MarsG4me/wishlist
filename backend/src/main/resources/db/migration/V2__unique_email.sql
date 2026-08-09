-- Emails identify an account for life: a second account with the same address must not
-- exist, not even while the first one is soft-deleted (that case is a reactivation).
--
-- One-time cleanup first: duplicates can only exist from dev environments where the
-- identity provider was rebuilt and JIT provisioning created a second row for the same
-- address. Keep the row that actually holds content (most wishlists + groups),
-- tie-break on the newest id; cascades remove the discarded empty rows.
WITH ranked AS (
    SELECT u.id,
           row_number() OVER (
               PARTITION BY lower(u.email)
               ORDER BY (SELECT count(*) FROM wishlists w WHERE w.users_id = u.id)
                        + (SELECT count(*) FROM groups g WHERE g.users_id = u.id) DESC,
                        u.id DESC
           ) AS rank
    FROM users u
)
DELETE FROM users WHERE id IN (SELECT id FROM ranked WHERE rank > 1);

CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
