package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "quantity_policy_operation", schema = "sales_ord")
@Getter
@NoArgsConstructor
public class SalesQuantityPolicyOperation extends BaseEntity {
  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private UUID lineId;

  @Column(nullable = false)
  private UUID actorId;

  @Column(nullable = false)
  private UUID operationId;

  @Column(nullable = false)
  private Long expectedVersion;

  @Column(nullable = false)
  private Long orderVersion;

  @Column(nullable = false, length = 500)
  private String reason;

  @Type(JsonType.class)
  @Column(nullable = false, columnDefinition = "jsonb")
  private SalesQuantityTermsDto result;

  public static SalesQuantityPolicyOperation record(
      UUID order,
      UUID line,
      UUID actor,
      UUID operation,
      Long expectedVersion,
      Long orderVersion,
      String reason,
      SalesQuantityTermsDto result) {
    var row = new SalesQuantityPolicyOperation();
    row.orderId = order;
    row.lineId = line;
    row.actorId = actor;
    row.operationId = operation;
    row.expectedVersion = expectedVersion;
    row.orderVersion = orderVersion;
    row.reason = reason;
    row.result = result;
    return row;
  }

  @Override
  public String getModuleCode() {
    return "QPOL";
  }
}
