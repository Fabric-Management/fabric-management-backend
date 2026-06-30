package com.fabricmanagement.procurement.quote.mapper;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.dto.ConvertedMoneyDto;
import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface SupplierQuoteMapper {

  @Mapping(target = "totalAmount", expression = "java(entity.computeTotalAmount())")
  SupplierQuoteResponse toResponse(SupplierQuote entity);

  @Mapping(target = "lineTotal", expression = "java(line.lineTotal())")
  SupplierQuoteResponse.QuoteLineResponse toLineResponse(SupplierQuoteLine line);

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
