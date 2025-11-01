package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.company.domain.Company;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * CompanyAddress junction entity - Links Company to Address.
 *
 * <p>Represents the relationship between a Company and their Address information.
 * Supports multiple addresses per company (headquarters, branches, warehouses, etc.).</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>✅ Multiple addresses per company</li>
 *   <li>✅ Primary address flag</li>
 *   <li>✅ Headquarters flag</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Link company to headquarters
 * CompanyAddress hqAddress = CompanyAddress.builder()
 *     .company(company)
 *     .address(hqAddressEntity)
 *     .isPrimary(true)
 *     .isHeadquarters(true)
 *     .build();
 *
 * // Link company to warehouse
 * CompanyAddress warehouseAddress = CompanyAddress.builder()
 *     .company(company)
 *     .address(warehouseAddressEntity)
 *     .isPrimary(false)
 *     .isHeadquarters(false)
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_company_address", schema = "common_communication",
    indexes = {
        @Index(name = "idx_company_address_company", columnList = "company_id"),
        @Index(name = "idx_company_address_address", columnList = "address_id"),
        @Index(name = "idx_company_address_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CompanyAddressId.class)
public class CompanyAddress extends BaseJunctionEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Id
    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", insertable = false, updatable = false)
    private Address address;

    /**
     * Primary address flag
     * <p>true = primary address for this company</p>
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    /**
     * Headquarters flag
     * <p>true = this is the company's headquarters</p>
     */
    @Column(name = "is_headquarters", nullable = false)
    @Builder.Default
    private Boolean isHeadquarters = false;

    /**
     * Set as primary address
     */
    public void setAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Mark as headquarters
     */
    public void setAsHeadquarters() {
        this.isHeadquarters = true;
    }

    @Override
    protected String getModuleCode() {
        return "CADR";
    }
}

