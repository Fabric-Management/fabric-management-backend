package com.fabricmanagement.costing.dto;

import com.fabricmanagement.costing.domain.price.PriceList;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only response for a PriceList. */
public record PriceListResponse(
    UUID id,
    String name,
    String moduleType,
    String currency,
    LocalDate validFrom,
    LocalDate validUntil,
    String seasonTag,
    boolean isActive) {

  public static PriceListResponse from(PriceList pl) {
    return new PriceListResponse(
        pl.getId(),
        pl.getName(),
        pl.getModuleType(),
        pl.getCurrency(),
        pl.getValidFrom(),
        pl.getValidUntil(),
        pl.getSeasonTag(),
        Boolean.TRUE.equals(pl.getIsActive()));
  }
}
