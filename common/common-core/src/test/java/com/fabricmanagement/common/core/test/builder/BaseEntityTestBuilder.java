package com.fabricmanagement.common.core.test.builder;

import com.fabricmanagement.common.core.domain.base.BaseEntity;
import com.fabricmanagement.common.core.test.util.TestUUIDs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base test data builder for entities extending BaseEntity.
 * Provides common setup for UUID-based entities in tests.
 */
public abstract class BaseEntityTestBuilder<T extends BaseEntity, B extends BaseEntityTestBuilder<T, B>> {
    
    protected UUID id;
    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;
    protected String createdBy;
    protected String updatedBy;
    protected Long version;
    protected Boolean deleted;
    
    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }
    
    public B withId(UUID id) {
        this.id = id;
        return self();
    }
    
    public B withRandomId() {
        this.id = UUID.randomUUID();
        return self();
    }
    
    public B withTestId(int sequence) {
        this.id = TestUUIDs.generateTestUUID("90", sequence);
        return self();
    }
    
    public B withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return self();
    }
    
    public B withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return self();
    }
    
    public B withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return self();
    }
    
    public B withUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return self();
    }
    
    public B withVersion(Long version) {
        this.version = version;
        return self();
    }
    
    public B withDeleted(Boolean deleted) {
        this.deleted = deleted;
        return self();
    }
    
    public B withDefaultValues() {
        this.id = TestUUIDs.random();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.createdBy = "test-user";
        this.updatedBy = "test-user";
        this.version = 0L;
        this.deleted = false;
        return self();
    }
    
    protected void applyBaseFields(T entity) {
        if (id != null) entity.setId(id);
        if (createdAt != null) entity.setCreatedAt(createdAt);
        if (updatedAt != null) entity.setUpdatedAt(updatedAt);
        if (createdBy != null) entity.setCreatedBy(createdBy);
        if (updatedBy != null) entity.setUpdatedBy(updatedBy);
        if (version != null) entity.setVersion(version);
        if (deleted != null) entity.setDeleted(deleted);
    }
    
    public abstract T build();
}