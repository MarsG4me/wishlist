package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.AdminAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntity, UUID> {
}
