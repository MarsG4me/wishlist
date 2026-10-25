package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One audited admin action; no soft delete, the purge job removes rows after a year. */
@Entity
@Table(name = "admin_audit")
public class AdminAuditEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "admin_users_id", nullable = false, updatable = false)
    private UUID adminUsersId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "action", nullable = false, updatable = false)
    private AdminAuditAction action;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    /** Raw JSON text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", updatable = false)
    private String detail;

    protected AdminAuditEntity() {
    }

    public AdminAuditEntity(UUID adminUsersId, AdminAuditAction action, UUID targetId, String detail) {
        this.adminUsersId = adminUsersId;
        this.action = action;
        this.targetId = targetId;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminUsersId() {
        return adminUsersId;
    }

    public AdminAuditAction getAction() {
        return action;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }
}
