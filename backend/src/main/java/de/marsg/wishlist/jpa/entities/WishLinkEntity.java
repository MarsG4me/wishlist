package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "host", length = 253)
    private String host;

    /** Written by rescans and admin decisions via bulk SQL only, never through this entity. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "blocked_by", insertable = false, updatable = false)
    private LinkBlockSource blockedBy;

    protected WishLinkEntity() {
    }

    public WishLinkEntity(String url, String label, short position, String host) {
        this.url = url;
        this.label = label;
        this.position = position;
        this.host = host;
    }

    /** Takes over label, position and host of an equal link from a new submission; url and id stay. */
    public void updateFrom(WishLinkEntity submitted) {
        this.label = submitted.label;
        this.position = submitted.position;
        this.host = submitted.host;
    }

    public String getHost() {
        return host;
    }

    public LinkBlockSource getBlockedBy() {
        return blockedBy;
    }

    public boolean isBlocked() {
        return blockedBy != null;
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
