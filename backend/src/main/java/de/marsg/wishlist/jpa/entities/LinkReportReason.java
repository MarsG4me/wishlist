package de.marsg.wishlist.jpa.entities;

/** Mirrors the native Postgres enum {@code link_report_reason}. */
public enum LinkReportReason {
    MALWARE_PHISHING,
    SCAM,
    ILLEGAL,
    CHILD_ABUSE,
    ADULT,
    OTHER
}
