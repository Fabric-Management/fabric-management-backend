package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

@Entity
@Table(schema = "sales_ord", name = "order_cover_line_result")
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCoverLineResult extends BaseEntity {
  @Column(name = "result_id", nullable = false, updatable = false)
  private UUID resultId;

  @Column(name = "line_id", nullable = false, updatable = false)
  private UUID lineId;

  @Column(nullable = false, updatable = false, length = 30)
  private String outcome;

  @Column(nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(nullable = false, updatable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "suitability_at_decision", nullable = false, updatable = false, length = 20)
  private OrderCoverEvidenceDto.Suitability suitabilityAtDecision;

  @Column(name = "requirement_profile_id", nullable = false, updatable = false)
  private UUID requirementProfileId;

  @Column(name = "requirement_profile_version", nullable = false, updatable = false)
  private int requirementProfileVersion;

  @Type(JsonType.class)
  @Column(
      name = "evidence_sources",
      nullable = false,
      updatable = false,
      columnDefinition = "jsonb")
  private List<OrderCoverEvidenceDto.Source> evidenceSources;

  @Column(name = "work_order_id", nullable = false, updatable = false)
  private UUID workOrderId;

  public static OrderCoverLineResult makeToOrder(
      UUID tenantId,
      UUID resultId,
      UUID lineId,
      BigDecimal quantity,
      String unit,
      OrderCoverEvidenceDto.Suitability suitability,
      UUID profileId,
      int profileVersion,
      List<OrderCoverEvidenceDto.Source> sources,
      UUID workOrderId) {
    var value = new OrderCoverLineResult();
    value.setTenantId(tenantId);
    value.resultId = resultId;
    value.lineId = lineId;
    value.outcome = "MAKE_TO_ORDER";
    value.quantity = quantity;
    value.unit = unit;
    value.suitabilityAtDecision = suitability;
    value.requirementProfileId = profileId;
    value.requirementProfileVersion = profileVersion;
    value.evidenceSources = List.copyOf(sources);
    value.workOrderId = workOrderId;
    return value;
  }

  @Override
  protected String getModuleCode() {
    return "OCLR";
  }
}
