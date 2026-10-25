package de.marsg.wishlist.jpa.entities;

/** Mirrors the native Postgres enum {@code link_block_source}: why a saved wish link is hidden. */
public enum LinkBlockSource {
    BLOCKLIST,
    WEB_RISK,
    SHORTENER,
    ADMIN,
    ADMIN_DOMAIN
}
