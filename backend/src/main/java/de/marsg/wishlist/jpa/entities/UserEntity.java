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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class UserEntity extends SoftDeletable {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    private UUID keycloakId;

    @Column(name = "displayname", nullable = false, length = 60)
    private String displayname;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "tier", nullable = false)
    private UserTier tier = UserTier.FREE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "locale", nullable = false)
    private UserLocale locale = UserLocale.EN;

    protected UserEntity() {
    }

    public UserEntity(UUID keycloakId, String displayname, String email, UserLocale locale) {
        this.keycloakId = keycloakId;
        this.displayname = displayname;
        this.email = email;
        this.locale = locale;
    }

    public UUID getId() {
        return id;
    }

    public UUID getKeycloakId() {
        return keycloakId;
    }

    public String getDisplayname() {
        return displayname;
    }

    public void setDisplayname(String displayname) {
        this.displayname = displayname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserTier getTier() {
        return tier;
    }

    public void setTier(UserTier tier) {
        this.tier = tier;
    }

    public UserLocale getLocale() {
        return locale;
    }

    public void setLocale(UserLocale locale) {
        this.locale = locale;
    }
}
