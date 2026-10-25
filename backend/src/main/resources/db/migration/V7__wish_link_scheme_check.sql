-- NOT VALID: legacy rows are not checked, every new or updated row must be http(s).
ALTER TABLE wish_links ADD CONSTRAINT chk_wish_links_url_scheme CHECK (url ~* '^https?://') NOT VALID;
