package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "group_wishlist_shares")
public class GroupWishlistShareEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "groups_id", nullable = false, updatable = false)
    private UUID groupsId;

    @Column(name = "wishlists_id", nullable = false, updatable = false)
    private UUID wishlistsId;

    protected GroupWishlistShareEntity() {
    }

    public GroupWishlistShareEntity(UUID groupsId, UUID wishlistsId) {
        this.groupsId = groupsId;
        this.wishlistsId = wishlistsId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupsId() {
        return groupsId;
    }

    public UUID getWishlistsId() {
        return wishlistsId;
    }
}
