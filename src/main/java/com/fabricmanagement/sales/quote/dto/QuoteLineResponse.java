package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.FulfillmentDeterminationMethod;
import com.fabricmanagement.sales.quote.domain.FulfillmentDeterminationStatus;
import com.fabricmanagement.sales.quote.domain.FulfillmentMode;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuoteLineDeliveryStatus;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class QuoteLineResponse {

  private final UUID id;
  private final UUID productId;
  private final String productDesc;
  private final UUID qualityGradeId;
  private final String qualityGradeCode;
  private final String qualityGradeName;
  private final BigDecimal qualityPriceFactor;
  private final UUID colorId;
  private final String colorCode;
  private final String colorName;
  private final String colorHex;
  private final List<QuoteLineLotSnapshot> selectedLots;
  private final QuoteLineDeliveryStatus deliveryStatus;
  private final LocalDate deliveryDate;
  private final Boolean deliveryCovered;

  @Schema(description = "Line-level fulfilment mode", nullable = true)
  private final FulfillmentMode fulfillmentMode;

  @Schema(description = "Current fulfilment determination status")
  private final FulfillmentDeterminationStatus fulfillmentDeterminationStatus;

  @Schema(description = "How the current fulfilment mode was determined", nullable = true)
  private final FulfillmentDeterminationMethod fulfillmentDeterminationMethod;

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
    this.qualityGradeId = line.getQualityGradeId();
    this.qualityGradeCode = line.getQualityGradeCode();
    this.qualityGradeName = line.getQualityGradeName();
    this.qualityPriceFactor = line.getQualityPriceFactor();
    this.colorId = line.getColorId();
    this.colorCode = line.getColorCode();
    this.colorName = line.getColorName();
    this.colorHex = line.getColorHex();
    this.selectedLots = QuoteLineLotSnapshotCodec.fromJson(line.getLotSnapshot());
    this.deliveryStatus = line.getDeliveryStatus();
    this.deliveryDate = line.getDeliveryDate();
    this.deliveryCovered = line.getDeliveryCovered();
    this.fulfillmentMode = line.getFulfillmentMode();
    this.fulfillmentDeterminationStatus = line.getFulfillmentDeterminationStatus();
    this.fulfillmentDeterminationMethod = line.getFulfillmentDeterminationMethod();
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
