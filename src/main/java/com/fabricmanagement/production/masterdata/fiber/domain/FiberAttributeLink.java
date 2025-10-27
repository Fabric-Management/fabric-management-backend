package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Fiber ↔ Attribute Many-to-Many link.
 * 
 * <p>A fiber can have multiple attributes (ORGANIC, RECYCLED, FAIRTRADE, etc.).</p>
 */
@Entity
@Table(name = "prod_fiber_attribute_link", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_attr_link_fiber", columnList = "fiber_id"),
        @Index(name = "idx_fiber_attr_link_attr", columnList = "attribute_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fiber_attribute", columnNames = {"fiber_id", "attribute_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberAttributeLink extends BaseEntity {

    @Column(name = "fiber_id", nullable = false)
    private UUID fiberId;  // FK → Fiber

    @Column(name = "attribute_id", nullable = false)
    private UUID attributeId;  // FK → FiberAttribute

    @Override
    protected String getModuleCode() {
        return "FAATR";
    }
}

