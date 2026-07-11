package com.fabricmanagement.procurement.quote.mapper;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.dto.ConvertedMoneyDto;
import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface SupplierQuoteMapper {

  @Mapping(target = "totalAmount", expression = "java(entity.computeTotalAmount())")
  SupplierQuoteResponse toResponse(
      SupplierQuote entity, @Context Map<UUID, SupplierRFQLine> rfqLinesById);

  @Mapping(target = "lineTotal", expression = "java(line.lineTotal())")
  @Mapping(target = "productId", expression = "java(productId(line.getRfqLineId(), rfqLinesById))")
  @Mapping(
      target = "productDesc",
      expression = "java(productDesc(line.getRfqLineId(), rfqLinesById))")
  SupplierQuoteResponse.QuoteLineResponse toLineResponse(
      SupplierQuoteLine line, @Context Map<UUID, SupplierRFQLine> rfqLinesById);

  default UUID productId(UUID rfqLineId, Map<UUID, SupplierRFQLine> rfqLinesById) {
    SupplierRFQLine rfqLine = rfqLinesById.get(rfqLineId);
    return rfqLine != null ? rfqLine.getProductId() : null;
  }

  default String productDesc(UUID rfqLineId, Map<UUID, SupplierRFQLine> rfqLinesById) {
    SupplierRFQLine rfqLine = rfqLinesById.get(rfqLineId);
    return rfqLine != null ? rfqLine.getProductDesc() : null;
  }

  default ConvertedMoneyDto toDto(ConvertedMoney money) {
    if (money == null) {
      return null;
    }
    return ConvertedMoneyDto.builder()
        .originalAmount(money.getOriginalAmount())
        .originalCurrency(money.getOriginalCurrency())
        .convertedAmount(money.getConvertedAmount())
        .convertedCurrency(money.getConvertedCurrency())
        .exchangeRate(money.getExchangeRate())
        .rateDate(money.getRateDate())
        .build();
  }
}
