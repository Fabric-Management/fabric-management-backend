package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence.PersistedCompetingAllocation;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCoverDisplayTest {
  @Test
  void competingAllocationsGainCurrentLineNumbersAndExplicitNullsForUnknownReferences()
      throws Exception {
    UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID(), missingId = UUID.randomUUID();
    SalesOrderLine first = line(firstId, "Natural cotton", null);
    SalesOrderLine second = line(secondId, null, UUID.randomUUID());
    var quantity = OrderCoverEvidenceDto.Quantity.known(BigDecimal.ONE, "kg");
    var evidence =
        new OrderCoverEvidenceDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            1,
            Instant.EPOCH,
            "fingerprint",
            "rule",
            List.of(
                new OrderCoverEvidenceDto.Line(
                    secondId,
                    1,
                    null,
                    quantity,
                    quantity,
                    quantity,
                    quantity,
                    OrderCoverEvidenceDto.Suitability.EXACT,
                    List.of(
                        new OrderCoverEvidenceDto.CompetingAllocation(firstId, quantity),
                        new OrderCoverEvidenceDto.CompetingAllocation(missingId, quantity)),
                    List.of(),
                    List.of(),
                    List.of())));

    var allocations =
        OrderCoverDisplay.enrich(evidence, List.of(first, second))
            .lines()
            .getFirst()
            .competingAllocations();

    assertThat(allocations.getFirst().lineNumber()).isEqualTo(1);
    assertThat(allocations.getFirst().label()).isEqualTo("Natural cotton");
    assertThat(allocations.getLast().lineNumber()).isNull();
    assertThat(allocations.getLast().label()).isNull();
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(allocations));
    assertThat(json.at("/1").has("lineNumber")).isTrue();
    assertThat(json.at("/1/lineNumber").isNull()).isTrue();
    assertThat(json.at("/1").has("label")).isTrue();
    assertThat(json.at("/1/label").isNull()).isTrue();
  }

  @Test
  void storedAllocationShapeOmitsTheReadTimeOnlyFields() throws Exception {
    var allocation =
        new PersistedCompetingAllocation(
            UUID.randomUUID(), OrderCoverEvidenceDto.Quantity.known(BigDecimal.ONE, "kg"));
    String json = new ObjectMapper().writeValueAsString(allocation);
    assertThat(json).doesNotContain("lineNumber", "label");
  }

  private static SalesOrderLine line(UUID id, String description, UUID productId) {
    SalesOrderLine line = mock(SalesOrderLine.class);
    when(line.getId()).thenReturn(id);
    when(line.getProductDesc()).thenReturn(description);
    when(line.getProductId()).thenReturn(productId);
    return line;
  }

  @Test
  void settlementProductCodeStaysNullableWhileTheDisplayLabelNeverFails() {
    UUID id = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    assertThat(OrderCoverDisplay.productCode(line(id, "Navy twill", product)))
        .isEqualTo("Navy twill");
    assertThat(OrderCoverDisplay.productCode(line(id, " ", product)))
        .isEqualTo("PRODUCT_" + product);
    assertThat(OrderCoverDisplay.productCode(line(id, null, null))).isNull();
    assertThat(OrderCoverDisplay.label(line(id, null, null))).isEqualTo(id.toString());
  }
}
