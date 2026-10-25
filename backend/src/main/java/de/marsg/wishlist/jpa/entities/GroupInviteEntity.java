package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Kept forever as join history: never hard-deleted, never purged. Revocation sets revokedAt. */
@Entity
@Table(name = "group_invites")
public class GroupInviteEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "groups_id", nullable = false, updatable = false)
    private UUID groupsId;

    /** Creator; {@code null} once their account was purged. */
    @Column(name = "users_id", updatable = false)
    private UUID usersId;

    @Column(name = "code", nullable = false, unique = true, updatable = false, length = 32)
    private String code;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** {@code null} = unlimited uses within the validity window. */
    @Column(name = "max_uses", updatable = false)
    private Integer maxUses;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    protected GroupInviteEntity() {
    }

    public GroupInviteEntity(UUID groupsId, UUID usersId, String code, Instant expiresAt, Integer maxUses) {
        this.groupsId = groupsId;
        this.usersId = usersId;
        this.code = code;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupsId() {
        return groupsId;
    }

    public UUID getUsersId() {
        return usersId;
    }

    public String getCode() {
        return code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUseCount() {
        return useCount;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExhausted() {
        return maxUses != null && useCount >= maxUses;
    }
}
