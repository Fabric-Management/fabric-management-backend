package com.fabricmanagement.procurement.quote.mapper;

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
}
