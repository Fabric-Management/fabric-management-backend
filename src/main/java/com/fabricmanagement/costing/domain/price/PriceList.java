package com.fabricmanagement.costing.domain.price;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 * A named price list for a module type, scoped by currency and validity period.
 *
 * <p>Multiple PriceLists can exist for the same tenant+moduleType, but only one should be {@code
 * active} on any given date. Seasonal pricing is tag-driven via {@code seasonTag}.
 */
@Entity
@Table(name = "price_list", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceList extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  @Column(name = "currency", nullable = false, length = 10)
  private String currency;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  /** Null means the price list is open-ended (effective indefinitely). */
  @Column(name = "valid_until")
  private LocalDate validUntil;

  /** Optional seasonal label (e.g. "2026-SPRING", "2025-Q3"). */
  @Column(name = "season_tag", length = 100)
  private String seasonTag;

  /**
   * Factory method.
   *
   * @param tenantId owning tenant
   * @param name human-readable name
   * @param moduleType target module (FIBER, YARN, …)
   * @param currency ISO currency code
   * @param validFrom start of validity
   * @param validUntil end of validity (nullable)
   * @param seasonTag optional seasonal label
   */
  public static PriceList create(
      java.util.UUID tenantId,
      String name,
      String moduleType,
      String currency,
      LocalDate validFrom,
      LocalDate validUntil,
      String seasonTag) {
    var pl = new PriceList();
    pl.setTenantId(tenantId);
    pl.setName(name);
    pl.setModuleType(moduleType);
    pl.setCurrency(currency);
    pl.setValidFrom(validFrom);
    pl.setValidUntil(validUntil);
    pl.setSeasonTag(seasonTag);
    pl.onCreate();
    return pl;
  }

  /**
   * Returns true when this price list is currently valid (date is between validFrom and
   * validUntil).
   */
  public boolean isValidOn(LocalDate date) {
    if (date.isBefore(validFrom)) return false;
    return validUntil == null || !date.isAfter(validUntil);
  }

  @Override
  protected String getModuleCode() {
    return "PLIST";
  }
}
