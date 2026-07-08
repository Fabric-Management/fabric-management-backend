package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuoteLineDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
@Schema(description = "Customer-facing quote line without internal pricing fields")
public class PublicQuoteLineResponse {

  private final UUID id;
  private final String productDesc;
  private final UUID colorId;
  private final String colorCode;
  private final String colorName;
  private final String colorHex;
  private final List<QuoteLineLotSnapshot> selectedLots;
  private final QuoteLineDeliveryStatus deliveryStatus;
  private final LocalDate deliveryDate;
  private final Boolean deliveryCovered;
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
    this.colorId = line.getColorId();
    this.colorCode = line.getColorCode();
    this.colorName = line.getColorName();
    this.colorHex = line.getColorHex();
    this.selectedLots = QuoteLineLotSnapshotCodec.fromJson(line.getLotSnapshot());
    this.deliveryStatus = line.getDeliveryStatus();
    this.deliveryDate = line.getDeliveryDate();
    this.deliveryCovered = line.getDeliveryCovered();
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
