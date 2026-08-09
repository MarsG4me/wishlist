package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishlistEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {

    Page<WishlistEntity> findByUsersId(UUID usersId, Pageable pageable);

    long countByUsersId(UUID usersId);
}
