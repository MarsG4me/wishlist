package de.marsg.wishlist.helpers;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * UUIDv7 (RFC 9562) generation and inspection.
 *
 * <p>Layout: 48-bit unix millisecond timestamp, 4-bit version (7), 12 random bits,
 * 2-bit variant (10), 62 random bits. Since the timestamp is embedded, entities do
 * not carry a created_at column — use {@link #timestampOf(UUID)} instead.
 */
public final class UuidV7Factory {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7Factory() {
    }

    public static UUID next() {
        long now = System.currentTimeMillis();
        long msb = (now << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
        long lsb = 0x8000000000000000L | (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }

    /** Extracts the creation instant embedded in a UUIDv7. */
    public static Instant timestampOf(UUID id) {
        if (id.version() != 7) {
            throw new IllegalArgumentException("Not a UUIDv7: " + id);
        }
        return Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16);
    }
}
