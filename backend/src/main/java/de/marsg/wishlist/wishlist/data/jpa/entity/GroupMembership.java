package de.marsg.wishlist.wishlist.data.jpa.entity;

import java.util.UUID;

import de.marsg.wishlist.wishlist.data.jpa.entity.special_ids.GroupMembershipId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "group_memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uniq_group_memberships_key", columnNames = { "group_id", "user_id" })
})
public class GroupMembership {

    @EmbeddedId
    private GroupMembershipId id;

    @ManyToOne
    @MapsId("groupId") // maps id.groupId to this field
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @MapsId("userId") // maps id.userId to this field
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin;

    // Constructors
    public GroupMembership(Group group, User user) {
        this.group = group;
        this.user = user;
        this.isAdmin = false;
        this.id = new GroupMembershipId(group.getId(), user.getKeycloakId());
    }
    
    protected GroupMembership() {
        // for JPA
    }

    /*
     * Getter
     */
    public GroupMembershipId getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    /*
     * Setter
     */

    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
}