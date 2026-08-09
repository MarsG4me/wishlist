package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "groups")
@SQLRestriction("deleted_at IS NULL")
public class GroupEntity extends SoftDeletable {

    @Id
    @UuidV7
    private UUID id;

    /**
     * Creator. Only informational — authority comes from the admin flag on memberships.
     * Transferred to another admin when the creator leaves, so the row never cascades
     * away with an account that is no longer part of the group.
     */
    @Column(name = "users_id", nullable = false)
    private UUID usersId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    protected GroupEntity() {
    }

    public GroupEntity(UUID usersId, String name, String description) {
        this.usersId = usersId;
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsersId() {
        return usersId;
    }

    public void transferCreatorTo(UUID usersId) {
        this.usersId = usersId;
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
}
