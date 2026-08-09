package de.marsg.wishlist.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ClaimParticipantId implements Serializable {

    @Column(name = "claims_id", nullable = false)
    private UUID claimsId;

    @Column(name = "users_id", nullable = false)
    private UUID usersId;

    protected ClaimParticipantId() {
    }

    public ClaimParticipantId(UUID claimsId, UUID usersId) {
        this.claimsId = claimsId;
        this.usersId = usersId;
    }

    public UUID getClaimsId() {
        return claimsId;
    }

    public UUID getUsersId() {
        return usersId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ClaimParticipantId other
                && Objects.equals(claimsId, other.claimsId)
                && Objects.equals(usersId, other.usersId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(claimsId, usersId);
    }
}
