package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fiber ↔ Certification Many-to-Many link.
 * 
 * <p>A fiber can have multiple certifications (GOTS, OEKO_TEX, FAIRTRADE, etc.).</p>
 */
@Entity
@Table(name = "prod_fiber_certification_link", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_cert_link_fiber", columnList = "fiber_id"),
        @Index(name = "idx_fiber_cert_link_cert", columnList = "certification_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fiber_certification", columnNames = {"fiber_id", "certification_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberCertificationLink extends BaseEntity {

    @Column(name = "fiber_id", nullable = false)
    private UUID fiberId;  // FK → Fiber

    @Column(name = "certification_id", nullable = false)
    private UUID certificationId;  // FK → FiberCertification

    /**
     * Certification number (e.g., "GOTS-2024-001234")
     */
    @Column(name = "cert_number", length = 100)
    private String certNumber;

    /**
     * Valid from date
     */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /**
     * Valid until date
     */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Override
    protected String getModuleCode() {
        return "FCERTL";
    }
}


