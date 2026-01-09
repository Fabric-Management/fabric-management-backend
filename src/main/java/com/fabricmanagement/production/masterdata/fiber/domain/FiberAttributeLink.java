package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseJunctionEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberAttribute;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Fiber ↔ Attribute Many-to-Many link - Junction Entity.
 *
 * <p>A fiber can have multiple attributes (ORGANIC, RECYCLED, FAIRTRADE, etc.).
 *
 * <p>This represents the many-to-many relationship between Fiber and FiberAttribute.
 *
 * <h2>Example:</h2>
 *
 * <pre>{@code
 * // Link ORGANIC attribute to a cotton fiber
 * FiberAttributeLink link = FiberAttributeLink.builder()
 *     .fiberId(cottonFiber.getId())
 *     .attributeId(organicAttribute.getId())
 *     .build();
 * }</pre>
 */
@Entity
@Table(
    name = "prod_fiber_attribute_link",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_attr_link_fiber", columnList = "fiber_id"),
      @Index(name = "idx_fiber_attr_link_attr", columnList = "attribute_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(FiberAttributeLinkId.class)
public class FiberAttributeLink extends BaseJunctionEntity {

  @Id
  @Column(name = "fiber_id", nullable = false)
  private UUID fiberId;

  @Id
  @Column(name = "attribute_id", nullable = false)
  private UUID attributeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiber_id", insertable = false, updatable = false)
  private Fiber fiber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attribute_id", insertable = false, updatable = false)
  private FiberAttribute attribute;

  @Override
  protected String getModuleCode() {
    return "FAATR";
  }
}
