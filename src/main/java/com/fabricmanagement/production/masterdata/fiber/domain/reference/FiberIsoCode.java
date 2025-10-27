package com.fabricmanagement.production.masterdata.fiber.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Fiber ISO Code - Reference table for fiber ISO 2076 codes.
 *
 * <p>Textile labeling standards (ISO 2076).</p>
 * <p><b>READ-ONLY:</b> System-defined, cannot be created/modified by tenants.</p>
 * <p>Can only be activated/deactivated.</p>
 */
@Entity
@Table(name = "prod_fiber_iso_code", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_iso_code", columnList = "iso_code"),
        @Index(name = "idx_fiber_iso_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberIsoCode extends BaseEntity {

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "iso_code", unique = true, nullable = false, length = 10, updatable = false)
    private String isoCode;

    @Column(name = "fiber_name", nullable = false, length = 255)
    private String fiberName;

    @Column(name = "fiber_type", length = 100)
    private String fiberType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_official_iso", nullable = false)
    private Boolean isOfficialIso;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Override
    protected String getModuleCode() {
        return "FISO";
    }
}


