package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A user report about a wish link; link and reporter ids go null when those rows are purged. */
@Entity
@Table(name = "link_reports")
public class LinkReportEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "wish_links_id", updatable = false)
    private UUID wishLinksId;

    @Column(name = "users_id", updatable = false)
    private UUID usersId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reason", nullable = false, updatable = false)
    private LinkReportReason reason;

    @Column(name = "comment", length = 500, updatable = false)
    private String comment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private LinkReportStatus status = LinkReportStatus.OPEN;

    @Column(name = "admin_users_id")
    private UUID adminUsersId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Raw JSON text of the evidence snapshot; only set when escalated. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence")
    private String evidence;

    protected LinkReportEntity() {
    }

    public LinkReportEntity(UUID wishLinksId, UUID usersId, LinkReportReason reason, String comment) {
        this.wishLinksId = wishLinksId;
        this.usersId = usersId;
        this.reason = reason;
        this.comment = comment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWishLinksId() {
        return wishLinksId;
    }

    public UUID getUsersId() {
        return usersId;
    }

    public LinkReportReason getReason() {
        return reason;
    }

    public String getComment() {
        return comment;
    }

    public LinkReportStatus getStatus() {
        return status;
    }

    public UUID getAdminUsersId() {
        return adminUsersId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getEvidence() {
        return evidence;
    }

    public void decide(LinkReportStatus newStatus, UUID adminId, Instant when, String evidenceJson) {
        this.status = newStatus;
        this.adminUsersId = adminId;
        this.decidedAt = when;
        this.evidence = evidenceJson;
    }
}
