package de.marsg.wishlist.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

/**
 * Base for main tables: rows are never hard-deleted directly, deletion sets
 * {@code deleted_at}. A purge script removes rows older than 30 days.
 * Subclasses must add {@code @SQLRestriction("deleted_at IS NULL")}.
 */
@MappedSuperclass
public abstract class SoftDeletable {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
