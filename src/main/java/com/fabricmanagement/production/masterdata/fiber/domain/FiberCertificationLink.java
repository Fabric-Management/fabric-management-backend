package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fiber ↔ Certification Many-to-Many link - Junction Entity.
 * 
 * <p>A fiber can have multiple certifications (GOTS, OEKO_TEX, FAIRTRADE, etc.).</p>
 * <p>This represents the many-to-many relationship between Fiber and FiberCertification.</p>
 * <p>Includes certification validity period and certificate number.</p>
 * 
 * <h2>Example:</h2>
 * <pre>{@code
 * // Link GOTS certification to organic cotton fiber
 * FiberCertificationLink link = FiberCertificationLink.builder()
 *     .fiberId(organicCottonFiber.getId())
 *     .certificationId(gotsCertification.getId())
 *     .certNumber("GOTS-2024-001234")
 *     .validFrom(LocalDate.of(2024, 1, 1))
 *     .validUntil(LocalDate.of(2025, 12, 31))
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "prod_fiber_certification_link", schema = "production",
    indexes = {
        @Index(name = "idx_fiber_cert_link_fiber", columnList = "fiber_id"),
        @Index(name = "idx_fiber_cert_link_cert", columnList = "certification_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(FiberCertificationLinkId.class)
public class FiberCertificationLink extends BaseJunctionEntity {

    @Id
    @Column(name = "fiber_id", nullable = false)
    private UUID fiberId;

    @Id
    @Column(name = "certification_id", nullable = false)
    private UUID certificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiber_id", insertable = false, updatable = false)
    private Fiber fiber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certification_id", insertable = false, updatable = false)
    private FiberCertification certification;

    /**
     * Certification number (e.g., "GOTS-2024-001234")
     * <p>Unique identifier provided by the certifying body.</p>
     */
    @Column(name = "cert_number", length = 100)
    private String certNumber;

    /**
     * Certification valid from date.
     * <p>When the certification became effective.</p>
     */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /**
     * Certification valid until date.
     * <p>When the certification expires (null = no expiry).</p>
     */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /**
     * Check if certification is currently valid.
     *
     * @return true if valid (no expiry or not yet expired)
     */
    public boolean isValid() {
        if (validUntil == null) {
            return true; // No expiry date = always valid
        }
        return !validUntil.isBefore(java.time.LocalDate.now());
    }

    /**
     * Check if certification is expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(java.time.LocalDate.now());
    }

    @Override
    protected String getModuleCode() {
        return "FCERTL";
    }
}


