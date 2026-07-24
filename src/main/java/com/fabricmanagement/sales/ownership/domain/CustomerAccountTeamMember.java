package com.fabricmanagement.sales.ownership.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tenant-scoped membership of an internal user in a customer's commercial account team. */
@Entity
@Table(
    name = "customer_account_team_member",
    schema = "sales",
    indexes = {
      @Index(
          name = "idx_customer_account_team_lookup",
          columnList = "tenant_id, customer_id, is_active, created_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_customer_account_team_member",
          columnNames = {"tenant_id", "customer_id", "user_id"})
    })
@IdClass(CustomerAccountTeamMemberId.class)
@Getter
@NoArgsConstructor
public class CustomerAccountTeamMember extends BaseJunctionEntity {

  @Id
  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  public static CustomerAccountTeamMember create(UUID customerId, UUID userId) {
    CustomerAccountTeamMember member = new CustomerAccountTeamMember();
    member.customerId = customerId;
    member.userId = userId;
    return member;
  }

  /** Domain-language alias for the inherited soft-delete operation. */
  public void deactivate() {
    delete();
  }

  @Override
  protected String getModuleCode() {
    return "CATM";
  }
}
