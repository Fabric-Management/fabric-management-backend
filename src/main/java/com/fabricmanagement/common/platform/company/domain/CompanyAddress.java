package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.communication.domain.Address;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * CompanyAddress junction entity - Links Company to Address.
 *
 * <p>Owned by Company module. Address entity lives in Communication (shared kernel).
 */
@Entity
@Table(
    name = "common_company_address",
    schema = "common_company",
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
public class CompanyAddress extends BaseJunctionEntity implements Assignable {

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

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  @Column(name = "is_headquarters", nullable = false)
  @Builder.Default
  private Boolean isHeadquarters = false;

  @Override
  public UUID getParentId() {
    return companyId;
  }

  @Override
  public UUID getChildId() {
    return addressId;
  }

  @Override
  public Boolean getPrimaryFlag() {
    return isPrimary;
  }

  @Override
  public void setPrimaryFlag(Boolean value) {
    this.isPrimary = Boolean.TRUE.equals(value);
  }

  @Override
  protected String getModuleCode() {
    return "CADR";
  }
}
