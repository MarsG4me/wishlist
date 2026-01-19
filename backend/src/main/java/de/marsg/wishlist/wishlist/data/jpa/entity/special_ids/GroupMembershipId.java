package de.marsg.wishlist.wishlist.data.jpa.entity.special_ids;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class GroupMembershipId implements Serializable  {

    @Column(name = "group_id", columnDefinition = "uuid")
    private UUID groupId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    protected GroupMembershipId() {}

    public GroupMembershipId(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    // equals and hashCode are mandatory for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupMembershipId)) return false;
        GroupMembershipId that = (GroupMembershipId) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }

    /*
        Getters
    */

    public UUID getGroupId(){
        return groupId;
    }    

    public UUID getUserId(){
        return userId;
    }

}
