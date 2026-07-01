package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteLineResponse {

  private final UUID id;
  private final UUID productId;
  private final String productDesc;
  private final BigDecimal requestedQty;
  private final String unit;
  private final BigDecimal listPrice;
  private final BigDecimal offeredPrice;
  private final String currency;
  private final BigDecimal discountRate;
  private final BigDecimal profitMargin;
  private final QuotePriceZone priceZone;
  private final String moduleSpecs;

  private QuoteLineResponse(QuoteLine line) {
    this.id = line.getId();
    this.productId = line.getProductId();
    this.productDesc = line.getProductDesc();
    this.requestedQty = line.getRequestedQty();
    this.unit = line.getUnit();
    this.listPrice = line.getListPrice();
    this.offeredPrice = line.getOfferedPrice();
    this.currency = line.getCurrency();
    this.discountRate = line.getDiscountRate();
    this.profitMargin = line.getProfitMargin();
    this.priceZone = line.getPriceZone();
    this.moduleSpecs = line.getModuleSpecs();
  }

  public static QuoteLineResponse from(QuoteLine line) {
    return new QuoteLineResponse(line);
  }
}
