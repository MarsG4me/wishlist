package de.marsg.wishlist.jpa.entities;

import de.marsg.wishlist.jpa.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "group_memberships")
public class GroupMembershipEntity {

    @Id
    @UuidV7
    private UUID id;

    @Column(name = "groups_id", nullable = false, updatable = false)
    private UUID groupsId;

    @Column(name = "users_id", nullable = false, updatable = false)
    private UUID usersId;

    /** Which invite was redeemed; {@code null} only for the owner's auto-membership. */
    @Column(name = "group_invites_id", updatable = false)
    private UUID groupInvitesId;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    protected GroupMembershipEntity() {
    }

    public GroupMembershipEntity(UUID groupsId, UUID usersId, UUID groupInvitesId, boolean isAdmin) {
        this.groupsId = groupsId;
        this.usersId = usersId;
        this.groupInvitesId = groupInvitesId;
        this.isAdmin = isAdmin;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupsId() {
        return groupsId;
    }

    public UUID getUsersId() {
        return usersId;
    }

    public UUID getGroupInvitesId() {
        return groupInvitesId;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
    }
}
