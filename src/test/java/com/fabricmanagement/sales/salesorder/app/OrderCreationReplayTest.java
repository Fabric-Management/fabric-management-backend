package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** ADR-0014 D10: a create request's fingerprint follows its content, not its key order. */
class OrderCreationReplayTest {

  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID KEY = UUID.randomUUID();

  private final OrderCreationReplay replay =
      new OrderCreationReplay(
          null, mock(DataSource.class), new ObjectMapper().findAndRegisterModules());

  private static CreateSalesOrderRequest request(String reference, Map<String, Object> metadata) {
    CreateSalesOrderRequest request = new CreateSalesOrderRequest();
    request.setPartnerId(CUSTOMER);
    request.setIdempotencyKey(KEY);
    request.setOrderDate(LocalDate.of(2026, 10, 2));
    request.setCustomerReference(reference);
    request.setMetadata(metadata);
    return request;
  }

  private static Map<String, Object> metadata(String first, String second) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put(first, first.equals("a") ? 1 : 2);
    value.put(second, second.equals("a") ? 1 : 2);
    return value;
  }

  @Test
  void aRepeatedRequestHasTheSameFingerprint() {
    assertThat(replay.fingerprint(request("PO-1", metadata("a", "b"))))
        .isEqualTo(replay.fingerprint(request("PO-1", metadata("a", "b"))))
        .hasSize(64);
  }

  @Test
  void theOrderOfMapEntriesDoesNotChangeIt() {
    assertThat(replay.fingerprint(request("PO-1", metadata("a", "b"))))
        .isEqualTo(replay.fingerprint(request("PO-1", metadata("b", "a"))));
  }

  @Test
  void otherContentHasAnotherFingerprint() {
    assertThat(replay.fingerprint(request("PO-1", null)))
        .isNotEqualTo(replay.fingerprint(request("PO-2", null)));
  }
}
