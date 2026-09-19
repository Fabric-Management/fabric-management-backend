package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/** One append-only requirement-profile version. */
@Entity
@Immutable
@Table(
    name = "requirement_profile_version",
    schema = "sales_ord",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_requirement_profile_version",
            columnNames = {"tenant_id", "profile_id", "profile_version"}),
    indexes = {
      @Index(
          name = "idx_requirement_profile_line",
          columnList = "tenant_id,sales_order_line_id,profile_version"),
      @Index(
          name = "idx_requirement_profile_lookup",
          columnList = "tenant_id,profile_id,profile_version")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RequirementProfileVersion extends BaseEntity {

  @Column(name = "profile_id", nullable = false, updatable = false)
  private UUID profileId;

  @Column(name = "profile_version", nullable = false, updatable = false)
  private Integer profileVersion;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "fingerprint", nullable = false, length = 64, updatable = false)
  private String fingerprint;

  @Type(JsonType.class)
  @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
  private RequirementProfileSnapshot snapshot;

  @Override
  protected String getModuleCode() {
    return "RPF";
  }
}
