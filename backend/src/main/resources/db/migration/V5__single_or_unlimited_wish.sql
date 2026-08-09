-- A wish is giftable once (quantity = 1) or unlimited (NULL). Arbitrary unit counts are
-- gone: several people funding one gift is the open-claim feature, not multiple units.
UPDATE wishes SET quantity = 1 WHERE quantity > 1;

ALTER TABLE wishes DROP CONSTRAINT chk_wishes_quantity;
ALTER TABLE wishes ADD CONSTRAINT chk_wishes_quantity CHECK (quantity IS NULL OR quantity = 1);
