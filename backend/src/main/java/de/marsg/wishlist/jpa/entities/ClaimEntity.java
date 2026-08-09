package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One claim = one gift unit of a wish. Co-claimers pool money as participants of one claim.
 * Hard-deleted when the last participant leaves — no soft delete.
 */
@Entity
@Table(name = "claims")
public class ClaimEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "wishes_id", nullable = false, updatable = false)
    private UUID wishesId;

    /** Creator. */
    @Column(name = "users_id", nullable = false, updatable = false)
    private UUID usersId;

    protected ClaimEntity() {
    }

    public ClaimEntity(UUID wishesId, UUID usersId) {
        this.wishesId = wishesId;
        this.usersId = usersId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWishesId() {
        return wishesId;
    }

    public UUID getUsersId() {
        return usersId;
    }
}
