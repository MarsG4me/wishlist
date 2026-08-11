package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.ClaimParticipantEntity;
import de.marsg.wishlist.jpa.entities.ClaimParticipantId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimParticipantRepository extends JpaRepository<ClaimParticipantEntity, ClaimParticipantId> {

    boolean existsByWishesIdAndIdUsersId(UUID wishesId, UUID usersId);

    Page<ClaimParticipantEntity> findByIdUsersId(UUID usersId, Pageable pageable);

    List<ClaimParticipantEntity> findByIdUsersId(UUID usersId);

    List<ClaimParticipantEntity> findByIdClaimsId(UUID claimsId);

    /** Every participation on a set of wishes — input of the revocation cleanup. */
    List<ClaimParticipantEntity> findByWishesIdIn(Collection<UUID> wishesIds);

    /** Participant rows with displaynames for a set of wishes (shared-view rendering). */
    @Query("""
            SELECT p.id.claimsId, p.wishesId, u.id, u.displayname
            FROM ClaimParticipantEntity p
            JOIN UserEntity u ON u.id = p.id.usersId
            WHERE p.wishesId IN :wishesIds
            """)
    List<Object[]> findParticipantRowsForWishes(@Param("wishesIds") Collection<UUID> wishesIds);
}
