package com.fabricmanagement.production.execution.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberAttribute;
import jakarta.persistence.*;
import lombok.*;

/**
 * Batch Attribute - Links a batch to a fiber attribute with a value.
 *
 * <p>Stores batch-specific attribute values (e.g., ORGANIC=true, RECYCLED=percentage).
 *
 * <p>References {@link FiberAttribute} for attribute definitions.
 */
@Entity
@Table(
    name = "production_execution_batch_attribute",
    schema = "production",
    indexes = {
      @Index(name = "idx_ba_tenant", columnList = "tenant_id"),
      @Index(name = "idx_ba_batch", columnList = "batch_id"),
      @Index(name = "idx_ba_attribute", columnList = "attribute_id"),
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_ba_batch_attribute",
          columnNames = {"batch_id", "attribute_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAttribute extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private Batch batch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attribute_id", nullable = false)
  private FiberAttribute attribute;

  @Column(name = "value", columnDefinition = "TEXT")
  private String value;

  @Override
  protected String getModuleCode() {
    return "BA";
  }
}
