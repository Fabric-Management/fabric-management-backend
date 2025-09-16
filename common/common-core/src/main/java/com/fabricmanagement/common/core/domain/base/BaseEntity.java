package com.fabricmanagement.common.core.domain.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Base entity class providing common fields and functionality for all entities.
 * Includes audit fields, optimistic locking, and soft delete capability.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;

    /**
     * Marks the entity as deleted (soft delete).
     */
    public void markAsDeleted() {
        this.deleted = Boolean.TRUE;
    }

    /**
     * Restores the entity from soft delete.
     */
    public void restore() {
        this.deleted = Boolean.FALSE;
    }

    /**
     * Checks if the entity is deleted.
     *
     * @return true if the entity is marked as deleted
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(this.deleted);
    }

    /**
     * Checks if the entity is new (not persisted yet).
     *
     * @return true if the entity is new
     */
    public boolean isNew() {
        return this.id == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;

        // If both IDs are null, entities are not equal (different instances)
        if (id == null || that.id == null) {
            return false;
        }

        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s{id=%d, version=%d, deleted=%s}",
            getClass().getSimpleName(), id, version, deleted);
    }
}
