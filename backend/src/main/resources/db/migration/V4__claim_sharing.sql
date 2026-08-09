-- Co-claiming is opt-in, decided by the claimer — never by the wish owner (who must
-- stay blind to all claim activity). A claim starts private: only its creator sees it
-- as joinable material, and only they can open it up for others to pool money in.
ALTER TABLE claims ADD COLUMN is_shared boolean NOT NULL DEFAULT false;
