package com.fabricmanagement.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class JsonUnwrappedIT {

  @Autowired private ObjectMapper objectMapper;

  @Data
  static class TestMoney {
    private BigDecimal amount;
    private String currency;
  }

  @Data
  static class FlatRequest {
    private String name;

    @JsonUnwrapped(prefix = "unitPrice")
    private TestMoney unitPrice;
  }

  @Test
  void testJsonUnwrapped() throws Exception {
    String json = "{\"name\":\"Test\",\"unitPriceamount\":100.5,\"unitPricecurrency\":\"GBP\"}";
    FlatRequest request = objectMapper.readValue(json, FlatRequest.class);
    assertThat(request.getName()).isEqualTo("Test");
    assertThat(request.getUnitPrice().getAmount()).isEqualTo(new BigDecimal("100.5"));
    assertThat(request.getUnitPrice().getCurrency()).isEqualTo("GBP");
  }
}
