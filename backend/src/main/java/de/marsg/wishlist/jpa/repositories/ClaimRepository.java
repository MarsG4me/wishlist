package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.ClaimEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<ClaimEntity, UUID> {

    long countByWishesId(UUID wishesId);

    List<ClaimEntity> findByWishesId(UUID wishesId);

    List<ClaimEntity> findByWishesIdIn(Collection<UUID> wishesIds);
}
