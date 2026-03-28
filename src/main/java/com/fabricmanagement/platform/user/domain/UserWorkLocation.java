package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "common_user_work_location",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_uwl_user", columnList = "user_id"),
      @Index(name = "idx_uwl_org_address", columnList = "org_address_id"),
      @Index(name = "idx_uwl_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserWorkLocationId.class)
public class UserWorkLocation extends BaseJunctionEntity implements Assignable {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "org_address_id", nullable = false)
  private UUID orgAddressId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "org_address_id",
      referencedColumnName = "address_id",
      insertable = false,
      updatable = false)
  private OrganizationAddress organizationAddress;

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  @Column(name = "notes", length = 255)
  private String notes;

  @Override
  public UUID getParentId() {
    return userId;
  }

  @Override
  public UUID getChildId() {
    return orgAddressId;
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
    return "UWLC";
  }
}
