package de.marsg.wishlist.jpa.entities;

/** Mirrors the native Postgres enum {@code link_report_status}. */
public enum LinkReportStatus {
    OPEN,
    DISMISSED,
    ACTIONED,
    ESCALATED
}
