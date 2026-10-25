package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMembershipRepository extends JpaRepository<GroupMembershipEntity, UUID> {

    Optional<GroupMembershipEntity> findByGroupsIdAndUsersId(UUID groupsId, UUID usersId);

    boolean existsByGroupsIdAndUsersId(UUID groupsId, UUID usersId);

    /** Permission check as a single boolean query — never by scanning a member list. */
    boolean existsByGroupsIdAndUsersIdAndIsAdminTrue(UUID groupsId, UUID usersId);

    List<GroupMembershipEntity> findByGroupsId(UUID groupsId);

    List<GroupMembershipEntity> findByUsersId(UUID usersId);

    /** Serializes last-admin checks and handovers on one group. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupEntity g WHERE g.id = :id")
    Optional<GroupEntity> findGroupForUpdate(@Param("id") UUID id);

    Page<GroupMembershipEntity> findByGroupsId(UUID groupsId, Pageable pageable);

    long countByGroupsIdAndIsAdminTrue(UUID groupsId);

    long countByGroupsId(UUID groupsId);

    /** Memberships of the user in non-deleted groups. */
    @Query("""
            SELECT COUNT(m) FROM GroupMembershipEntity m
            JOIN GroupEntity g ON g.id = m.groupsId
            WHERE m.usersId = :userId
            """)
    long countInLiveGroups(@Param("userId") UUID userId);

    /** True when both users are members of at least one common (non-deleted) group. */
    @Query("""
            SELECT COUNT(m1) > 0 FROM GroupMembershipEntity m1
            JOIN GroupMembershipEntity m2 ON m2.groupsId = m1.groupsId
            JOIN GroupEntity g ON g.id = m1.groupsId
            WHERE m1.usersId = :userA AND m2.usersId = :userB
            """)
    boolean existsGroupWithBothUsers(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
