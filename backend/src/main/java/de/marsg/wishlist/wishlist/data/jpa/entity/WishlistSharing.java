package de.marsg.wishlist.wishlist.data.jpa.entity;

import de.marsg.wishlist.wishlist.data.jpa.entity.special_ids.WishlistSharingId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "wishlist_sharing", uniqueConstraints = {
        @UniqueConstraint(name = "uniq_wishlist_sharing_key", columnNames = { "group_id", "wishlist_id" })
}, indexes = {
        @Index(name = "idx_wishlistsharing_group", columnList = "group_id"),
        @Index(name = "idx_wishlistsharing_list", columnList = "wishlist_id")
})
public class WishlistSharing {

    @EmbeddedId
    private WishlistSharingId id;

    @ManyToOne
    @MapsId("groupId") // maps id.groupId to this field
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @MapsId("wishlistId") // maps id.userId to this field
    @JoinColumn(name = "wishlist_id", nullable = false)
    private Wishlist wishlist;

    // Constructors
    public WishlistSharing(Group group, Wishlist wishlist) {
        this.group = group;
        this.wishlist = wishlist;
        this.id = new WishlistSharingId(group.getId(), wishlist.getId());
    }

    protected WishlistSharing() {
        // for JPA
    }

    /*
     * Getter
     */
    public WishlistSharingId getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Wishlist getWishlist() {
        return wishlist;
    }

}