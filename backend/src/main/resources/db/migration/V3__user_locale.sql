-- The interface language becomes an account setting owned by this app: the frontend
-- reads it from /v1/users/me instead of the Keycloak Account API (which rejects the
-- app token with 401 because it lacks the `account` audience). Keycloak still gets
-- the value mirrored into its `locale` attribute for its own login pages and emails.
CREATE TYPE user_locale AS ENUM ('DE', 'EN');

ALTER TABLE users ADD COLUMN locale user_locale NOT NULL DEFAULT 'EN';
