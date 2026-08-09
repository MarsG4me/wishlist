package de.marsg.wishlist.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "claim_participants")
public class ClaimParticipantEntity {

    @EmbeddedId
    private ClaimParticipantId id;

    /** Denormalized: enforces one participation per user per wish (unique wishes_id + users_id). */
    @Column(name = "wishes_id", nullable = false, updatable = false)
    private UUID wishesId;

    protected ClaimParticipantEntity() {
    }

    public ClaimParticipantEntity(UUID claimsId, UUID usersId, UUID wishesId) {
        this.id = new ClaimParticipantId(claimsId, usersId);
        this.wishesId = wishesId;
    }

    public ClaimParticipantId getId() {
        return id;
    }

    public UUID getClaimsId() {
        return id.getClaimsId();
    }

    public UUID getUsersId() {
        return id.getUsersId();
    }

    public UUID getWishesId() {
        return wishesId;
    }
}
