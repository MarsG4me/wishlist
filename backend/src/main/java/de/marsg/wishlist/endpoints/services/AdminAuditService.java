package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import de.marsg.wishlist.jpa.entities.AdminAuditEntity;
import de.marsg.wishlist.jpa.repositories.AdminAuditRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Writes the admin audit trail inside the caller's writable transaction: no committed action, no row. */
@Service
public class AdminAuditService {

    private final AdminAuditRepository repository;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** {@code targetId} and {@code detail} may be null; detail must never hold personal data such as a search term. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID adminId, AdminAuditAction action, UUID targetId, Map<String, Object> detail) {
        String json = detail == null ? null : objectMapper.writeValueAsString(detail);
        repository.save(new AdminAuditEntity(adminId, action, targetId, json));
    }
}
