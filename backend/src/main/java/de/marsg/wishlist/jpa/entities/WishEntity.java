package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "wishes")
@SQLRestriction("deleted_at IS NULL")
public class WishEntity extends SoftDeletable {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "wishlists_id", nullable = false, updatable = false)
    private UUID wishlistsId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** {@code null} = giftable unlimited times. */
    @Column(name = "quantity")
    private Integer quantity;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "wishes_id", nullable = false)
    @OrderBy("position ASC")
    private List<WishLinkEntity> links = new ArrayList<>();

    protected WishEntity() {
    }

    public WishEntity(UUID wishlistsId, String name, String description, Integer quantity) {
        this.wishlistsId = wishlistsId;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWishlistsId() {
        return wishlistsId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public List<WishLinkEntity> getLinks() {
        return links;
    }

    public void replaceLinks(List<WishLinkEntity> newLinks) {
        links.clear();
        links.addAll(newLinks);
    }
}
