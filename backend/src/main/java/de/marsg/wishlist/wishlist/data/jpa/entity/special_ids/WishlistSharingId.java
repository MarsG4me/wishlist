package de.marsg.wishlist.wishlist.data.jpa.entity.special_ids;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class WishlistSharingId implements Serializable  {

    @Column(name = "group_id", columnDefinition = "uuid")
    private UUID groupId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID wishlistId;

    protected WishlistSharingId() {}

    public WishlistSharingId(UUID groupId, UUID wishlistId) {
        this.groupId = groupId;
        this.wishlistId = wishlistId;
    }

    // equals and hashCode are mandatory for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WishlistSharingId)) return false;
        WishlistSharingId that = (WishlistSharingId) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(wishlistId, that.wishlistId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, wishlistId);
    }

    /*
        Getters
    */

    public UUID getGroupId(){
        return groupId;
    }    

    public UUID getWishlistId(){
        return wishlistId;
    }

}
