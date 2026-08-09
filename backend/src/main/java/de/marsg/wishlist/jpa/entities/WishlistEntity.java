package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "wishlists")
@SQLRestriction("deleted_at IS NULL")
public class WishlistEntity extends SoftDeletable {

    @Id
    @UuidV7
    private UUID id;

    /** Owner. */
    @Column(name = "users_id", nullable = false, updatable = false)
    private UUID usersId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected WishlistEntity() {
    }

    public WishlistEntity(UUID usersId, String name) {
        this.usersId = usersId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsersId() {
        return usersId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
