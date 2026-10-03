package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repeated order creation with one client key (ADR-0014 D10). Creates with the same key are
 * serialised on a transaction-scoped advisory lock, so a concurrent repeat waits for the first and
 * then receives the order it created. The same key with different content is a conflict.
 */
@Component
public class OrderCreationReplay {

  private static final TypeReference<Map<String, Object>> AS_MAP = new TypeReference<>() {};

  private final SalesOrderRepository orders;
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public OrderCreationReplay(
      SalesOrderRepository orders, DataSource dataSource, ObjectMapper objectMapper) {
    this.orders = orders;
    this.jdbc = new JdbcTemplate(dataSource);
    this.objectMapper = objectMapper;
  }

  /**
   * Takes the key for this transaction and returns the order it already created, if any. Must run
   * inside the creating transaction: the lock is released when that transaction ends.
   */
  public Optional<SalesOrder> claim(UUID tenantId, UUID key, String fingerprint) {
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        (rs, row) -> true,
        tenantId + ":" + key + ":SALES_ORDER_CREATE");
    Optional<SalesOrder> created = orders.findByTenantIdAndCreationKey(tenantId, key);
    if (created.isPresent() && !fingerprint.equals(created.get().getCreationRequestHash())) {
      throw OrderDomainException.stage(
          "IDEMPOTENCY_KEY_REUSED",
          "This request key already created an order with different content");
    }
    return created;
  }

  /** A stable fingerprint of the request content (keys sorted at every level). */
  public String fingerprint(CreateSalesOrderRequest request) {
    try {
      Map<String, Object> content = objectMapper.convertValue(request, AS_MAP);
      byte[] canonical =
          objectMapper
              .writer()
              .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
              .writeValueAsString(content)
              .getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Cannot fingerprint the create request", ex);
    }
  }
}
