package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

    long countByUsersId(UUID usersId);

    @Query("""
            SELECT g FROM GroupEntity g
            JOIN GroupMembershipEntity m ON m.groupsId = g.id
            WHERE m.usersId = :userId
            """)
    Page<GroupEntity> findGroupsOfUser(@Param("userId") UUID userId, Pageable pageable);
}
