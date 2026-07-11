package com.fabricmanagement.procurement.quote.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class SupplierQuoteMapperTest {

  private final SupplierQuoteMapper mapper = Mappers.getMapper(SupplierQuoteMapper.class);

  @Test
  void shouldEnrichQuoteLineWithProductFieldsFromMatchingRfqLine() {
    UUID rfqLineId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    SupplierQuoteLine quoteLine = new SupplierQuoteLine();
    quoteLine.setRfqLineId(rfqLineId);
    quoteLine.setUnitPrice(new BigDecimal("12.50"));
    quoteLine.setCurrency("GBP");
    quoteLine.setQty(new BigDecimal("4.000"));
    quoteLine.setUnit("kg");

    SupplierQuote quote = new SupplierQuote();
    quote.addLine(quoteLine);

    SupplierRFQLine rfqLine = new SupplierRFQLine();
    rfqLine.setId(rfqLineId);
    rfqLine.setProductId(productId);
    rfqLine.setProductDesc("Combed cotton yarn");

    SupplierQuoteResponse response = mapper.toResponse(quote, Map.of(rfqLineId, rfqLine));
    SupplierQuoteResponse.QuoteLineResponse mappedLine = response.getLines().getFirst();

    assertEquals(productId, mappedLine.getProductId());
    assertEquals("Combed cotton yarn", mappedLine.getProductDesc());
    assertEquals(new BigDecimal("12.50"), mappedLine.getUnitPrice());
    assertEquals(new BigDecimal("4.000"), mappedLine.getQty());
  }
}
