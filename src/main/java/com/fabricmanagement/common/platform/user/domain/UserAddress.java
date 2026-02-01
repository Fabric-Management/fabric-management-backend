package com.fabricmanagement.common.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.Assignable;
import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.common.platform.communication.domain.Address;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * UserAddress junction entity - Links User to Address.
 *
 * <p>Owned by User module. Address entity lives in Communication (shared kernel).
 */
@Entity
@Table(
    name = "common_user_address",
    schema = "common_user",
    indexes = {
      @Index(name = "idx_user_address_user", columnList = "user_id"),
      @Index(name = "idx_user_address_address", columnList = "address_id"),
      @Index(name = "idx_user_address_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserAddressId.class)
public class UserAddress extends BaseJunctionEntity implements Assignable {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "address_id", nullable = false)
  private UUID addressId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "address_id", insertable = false, updatable = false)
  private Address address;

  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean isPrimary = false;

  @Column(name = "is_work_address", nullable = false)
  @Builder.Default
  private Boolean isWorkAddress = false;

  @Override
  public UUID getParentId() {
    return userId;
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
    return "UADR";
  }
}
