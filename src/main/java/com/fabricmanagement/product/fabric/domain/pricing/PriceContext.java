package com.fabricmanagement.product.fabric.domain.pricing;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What is known about the lot a finishing price is looked up for. Anything left null is unknown,
 * and a price row that is narrowed by something unknown does not apply.
 *
 * @param priceClassId the dyehouse tier the firm put the colour in
 * @param formation woven, knitted or nonwoven
 * @param gsmGreige weight of the greige fabric
 * @param gsmFinished weight of the finished fabric
 */
public record PriceContext(
    UUID priceClassId, FabricFormation formation, BigDecimal gsmGreige, BigDecimal gsmFinished) {

  /** Nothing is known: only prices that are not narrowed apply. */
  public static PriceContext unknown() {
    return new PriceContext(null, null, null, null);
  }
}
