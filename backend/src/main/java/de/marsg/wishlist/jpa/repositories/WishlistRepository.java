package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishlistEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {

    List<WishlistEntity> findByUsersIdOrderByIdDesc(UUID usersId);

    long countByUsersId(UUID usersId);
}
