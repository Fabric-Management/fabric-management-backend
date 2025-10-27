package com.fabricmanagement.production.masterdata.fiber.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Fiber Certification - Reference table for certifications.
 *
 * <p>Defines independent third-party certifications for fibers.</p>
 * <p><b>READ-ONLY:</b> System-defined, cannot be created/modified by tenants.</p>
 * <p>Can only be activated/deactivated.</p>
 */
@Entity
@Table(name = "prod_fiber_certification", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_certification_code", columnList = "certification_code"),
        @Index(name = "idx_fiber_certification_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberCertification extends BaseEntity {

    @Column(name = "certification_code", unique = true, nullable = false, length = 50, updatable = false)
    private String certificationCode;

    @Column(name = "certification_name", nullable = false, length = 100)
    private String certificationName;

    @Column(name = "certifying_body", length = 255)
    private String certifyingBody;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Override
    protected String getModuleCode() {
        return "FCERT";
    }
}

