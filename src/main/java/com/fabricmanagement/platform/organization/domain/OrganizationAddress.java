package com.fabricmanagement.platform.organization.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.platform.communication.domain.Address;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * OrganizationAddress junction entity - Links Organization to Address.
 *
 * <p>Owned by Organization module. Address entity lives in Communication (shared kernel).
 */
@Entity
@Table(
    name = "common_organization_address",
    schema = "common_company",
    indexes = {
      @Index(name = "idx_org_address_org", columnList = "organization_id"),
      @Index(name = "idx_org_address_address", columnList = "address_id"),
      @Index(name = "idx_org_address_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(OrganizationAddressId.class)
public class OrganizationAddress extends BaseJunctionEntity implements Assignable {

  @Id
  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Id
  @Column(name = "address_id", nullable = false)
  private UUID addressId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id", insertable = false, updatable = false)
  private Organization organization;

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
    return organizationId;
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
    return "OADR";
  }
}
