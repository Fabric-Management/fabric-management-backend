package com.fabricmanagement.costing.domain.currency;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

/**
 * Historical price record for a cost item, enabling trend analysis (e.g. "How has cotton price
 * changed over the last 2 years?", "Which season is most expensive?").
 *
 * <p>A new row is appended each time the price of a cost item changes, preserving the full audit
 * trail. The previous row's {@code validUntil} should be set to the day before the new row's {@code
 * validFrom}.
 */
@Entity
@Table(name = "cost_history", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostHistory extends BaseEntity {

  /** References {@code costing.cost_item.code}. */
  @Column(name = "cost_item_code", nullable = false, length = 50)
  private String costItemCode;

  @Column(name = "module_type", length = 50)
  private String moduleType;

  @Column(name = "material_id")
  private UUID materialId;

  @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "currency", nullable = false, length = 10)
  @Builder.Default
  private String currency = "TRY";

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  /** Null = current (not yet superseded). */
  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "change_reason", columnDefinition = "TEXT")
  private String changeReason;

  @Column(name = "season_tag", length = 100)
  private String seasonTag;

  @Override
  protected String getModuleCode() {
    return "CHIST";
  }
}
