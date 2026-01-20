package de.marsg.wishlist.wishlist.data.jpa.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.marsg.wishlist.wishlist.data.jpa.entity.Group;
import de.marsg.wishlist.wishlist.data.jpa.entity.GroupMembership;
import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.entity.special_ids.GroupMembershipId;
import jakarta.transaction.Transactional;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, GroupMembershipId> {

    @Query(value = "SELECT g.* FROM groups g JOIN group_memberships gm ON g.id = gm.group_id WHERE gm.user_id = :userId LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Group> findAllGroupsByUserId(@Param("userId") UUID userId, @Param("limit") int limit,
            @Param("offset") int offset);

    default List<Group> findAllGroupsByUserId(@Param("userId") UUID userId, @Param("offset") int offset) {
        return findAllGroupsByUserId(userId, 20, offset);
    }

    @Query(value = "SELECT Count(g.*) FROM groups g JOIN group_memberships gm ON g.id = gm.group_id WHERE gm.user_id = :userId", nativeQuery = true)
    long countUsersGroups(@Param("userId") UUID userId);

    @Query(value = "SELECT u.* FROM users u JOIN group_memberships gm ON u.keycloak_id = gm.user_id WHERE gm.group_id = :groupId LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<User> findAllUsersByGroupId(@Param("groupId") UUID groupId, @Param("limit") int limit,
            @Param("offset") int offset);

    default List<User> findAllUsersByGroupId(@Param("groupId") UUID groupId, @Param("offset") int offset) {
        return findAllUsersByGroupId(groupId, 50, offset);
    }

    @Query(value = "SELECT COUNT(u.*) FROM users u JOIN group_memberships gm ON u.keycloak_id = gm.user_id WHERE gm.group_id = :groupId", nativeQuery = true)
    long countGroupMembers(@Param("groupId") UUID groupId);

    @Query("SELECT CASE WHEN COUNT(gm) > 0 THEN true ELSE false END FROM GroupMembership gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    boolean existsByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(gm1) > 0 THEN true ELSE false END FROM GroupMembership gm1 JOIN GroupMembership gm2 ON gm1.id.groupId = gm2.id.groupId WHERE gm1.id.userId = :userId1 AND gm2.id.userId = :userId2")
    boolean existsGroupWithBothUsers(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    @Query("SELECT DISTINCT g FROM Group g JOIN GroupMembership gm1 ON g.id = gm1.id.groupId JOIN GroupMembership gm2 ON g.id = gm2.id.groupId WHERE gm1.id.userId = :userId1 AND gm2.id.userId = :userId2")
    List<Group> findGroupsWithBothUsers(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    @Query("SELECT gm FROM GroupMembership gm WHERE gm.id.groupId = :groupId AND gm.id.userId = :userId")
    Optional<GroupMembership> findMembershipByGroupIdAndUserId(@Param("groupId") UUID groupId,
            @Param("userId") UUID userId);

    @Modifying
    @Transactional
    void deleteByGroupId(UUID groupId);
}
