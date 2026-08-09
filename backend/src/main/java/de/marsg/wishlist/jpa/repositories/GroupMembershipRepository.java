package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMembershipRepository extends JpaRepository<GroupMembershipEntity, UUID> {

    Optional<GroupMembershipEntity> findByGroupsIdAndUsersId(UUID groupsId, UUID usersId);

    boolean existsByGroupsIdAndUsersId(UUID groupsId, UUID usersId);

    List<GroupMembershipEntity> findByGroupsId(UUID groupsId);

    /** True when both users are members of at least one common (non-deleted) group. */
    @Query("""
            SELECT COUNT(m1) > 0 FROM GroupMembershipEntity m1
            JOIN GroupMembershipEntity m2 ON m2.groupsId = m1.groupsId
            JOIN GroupEntity g ON g.id = m1.groupsId
            WHERE m1.usersId = :userA AND m2.usersId = :userB
            """)
    boolean existsGroupWithBothUsers(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
