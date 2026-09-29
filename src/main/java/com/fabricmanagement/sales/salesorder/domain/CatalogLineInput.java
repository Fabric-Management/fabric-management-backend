package com.fabricmanagement.sales.salesorder.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** The distribution fields every catalogue-line write path validates the same way (SOI R02–R06). */
public interface CatalogLineInput {

  UUID getProductId();

  String getUnit();

  UUID getColorId();

  BigDecimal getFinishedWidth();

  String getFinishedWidthUnit();

  LocalDate getRequestedDeliveryDate();
}
