package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.pricing.PriceContext;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Which finishing prices apply to a piece of work: the process, the firm (null for work at an own
 * site), the date, and what is known about the lot: the dyehouse's price class for its colour, the
 * fabric's formation and its weights. Whatever is null is unknown.
 */
public record FinishingPriceQuery(
    UUID processId,
    UUID partnerId,
    LocalDate date,
    UUID priceClassId,
    FabricFormation formation,
    BigDecimal gsmGreige,
    BigDecimal gsmFinished) {

  public PriceContext context() {
    return new PriceContext(priceClassId, formation, gsmGreige, gsmFinished);
  }
}
