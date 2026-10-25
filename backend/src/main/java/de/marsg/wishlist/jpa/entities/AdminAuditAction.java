package de.marsg.wishlist.jpa.entities;

/** Mirrors the native Postgres enum type {@code admin_audit_action}; new values need a migration too. */
public enum AdminAuditAction {
    USER_SEARCH,
    USER_EXPORT,
    USER_DELETE,
    USER_DELETE_IMMEDIATE,
    TIER_CHANGE,
    LINK_REPORT_DECISION,
    LINK_REPORT_EXPORT,
    DOMAIN_UNBLOCK,
    LINK_UNBLOCK
}
