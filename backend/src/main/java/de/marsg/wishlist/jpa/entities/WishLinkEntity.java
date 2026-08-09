package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "wish_links")
public class WishLinkEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "position", nullable = false)
    private short position;

    protected WishLinkEntity() {
    }

    public WishLinkEntity(String url, String label, short position) {
        this.url = url;
        this.label = label;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getLabel() {
        return label;
    }

    public short getPosition() {
        return position;
    }
}
