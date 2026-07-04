package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
@Schema(description = "Customer-facing quote line without internal pricing fields")
public class PublicQuoteLineResponse {

  private final UUID id;
  private final String productDesc;
  private final BigDecimal requestedQty;
  private final String unit;
  private final BigDecimal listPrice;
  private final BigDecimal offeredPrice;
  private final String currency;
  private final BigDecimal discountRate;
  private final String moduleSpecs;

  private PublicQuoteLineResponse(QuoteLine line) {
    this.id = line.getId();
    this.productDesc = line.getProductDesc();
    this.requestedQty = line.getRequestedQty();
    this.unit = line.getUnit();
    this.listPrice = line.getListPrice();
    this.offeredPrice = line.getOfferedPrice();
    this.currency = line.getCurrency();
    this.discountRate = line.getDiscountRate();
    this.moduleSpecs = line.getModuleSpecs();
  }

  public static PublicQuoteLineResponse from(QuoteLine line) {
    return new PublicQuoteLineResponse(line);
  }
}
