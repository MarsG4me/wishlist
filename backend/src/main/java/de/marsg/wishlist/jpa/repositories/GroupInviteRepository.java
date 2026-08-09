package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInviteRepository extends JpaRepository<GroupInviteEntity, UUID> {

    Optional<GroupInviteEntity> findByCode(String code);

    List<GroupInviteEntity> findByGroupsIdOrderByIdDesc(UUID groupsId);

    /**
     * Atomically consumes one use. Returns 0 when the invite is already exhausted,
     * so concurrent joins cannot exceed max_uses.
     */
    @Modifying
    @Query("""
            UPDATE GroupInviteEntity i SET i.useCount = i.useCount + 1
            WHERE i.id = :id AND (i.maxUses IS NULL OR i.useCount < i.maxUses)
            """)
    int consumeUse(@Param("id") UUID id);
}
