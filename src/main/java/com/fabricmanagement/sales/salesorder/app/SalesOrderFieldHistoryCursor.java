package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The opaque position in an order's field-history chain (CEDIT-09 §3.2): format, tenant and order
 * it belongs to, the chain's snapshot version and the last {@code (orderVersion, id)} pair shown.
 * It is no access proof and holds no server state; access is checked on every page. A cursor of
 * another tenant or order, of an unknown format or with impossible bounds is refused as a
 * VALIDATION_ERROR (422) naming {@code cursor}.
 */
record SalesOrderFieldHistoryCursor(
    UUID tenantId, UUID orderId, long snapshotVersion, long lastVersion, UUID lastId) {

  static final String FORMAT = "1";
  static final String CURSOR = "cursor";
  static final String LIMIT = "limit";

  private static final String SEPARATOR = "|";
  private static final Pattern BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");

  SalesOrderFieldHistoryCursor {
    Objects.requireNonNull(tenantId, "A cursor has a tenant");
    Objects.requireNonNull(orderId, "A cursor has an order");
    Objects.requireNonNull(lastId, "A cursor has a last entry");
    if (lastVersion < 0 || lastVersion > snapshotVersion) {
      throw new IllegalArgumentException("A cursor's last version lies within its snapshot");
    }
  }

  /** The page size: the default when none is asked, otherwise 1 to the maximum. */
  static int limit(Integer requested) {
    if (requested == null) {
      return SalesOrderFieldHistoryDtos.DEFAULT_LIMIT;
    }
    if (requested < 1 || requested > SalesOrderFieldHistoryDtos.MAX_LIMIT) {
      throw invalid(LIMIT, "must be between 1 and " + SalesOrderFieldHistoryDtos.MAX_LIMIT);
    }
    return requested;
  }

  /** The text a client sends back unchanged. */
  String encode() {
    String plain =
        String.join(
            SEPARATOR,
            FORMAT,
            tenantId.toString(),
            orderId.toString(),
            Long.toString(snapshotVersion),
            Long.toString(lastVersion),
            lastId.toString());
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Reads a cursor sent for this tenant's order. Format and context are checked here, before any
   * database read; whether its snapshot still lies within the order is checked by the caller.
   */
  static SalesOrderFieldHistoryCursor decode(String text, UUID tenantId, UUID orderId) {
    if (text == null
        || text.length() > SalesOrderFieldHistoryDtos.MAX_CURSOR_LENGTH
        || !BASE64URL.matcher(text).matches()) {
      throw unreadable();
    }
    String plain;
    try {
      plain = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformed) {
      throw unreadable();
    }
    String[] parts = plain.split(Pattern.quote(SEPARATOR), -1);
    if (parts.length != 6 || !FORMAT.equals(parts[0])) {
      throw unreadable();
    }
    UUID cursorTenant = canonicalUuid(parts[1]);
    UUID cursorOrder = canonicalUuid(parts[2]);
    long snapshot = nonNegative(parts[3]);
    long last = nonNegative(parts[4]);
    UUID lastId = canonicalUuid(parts[5]);
    if (!cursorTenant.equals(tenantId) || !cursorOrder.equals(orderId)) {
      throw invalid(CURSOR, "belongs to another order's history");
    }
    if (last > snapshot) {
      throw unreadable();
    }
    return new SalesOrderFieldHistoryCursor(cursorTenant, cursorOrder, snapshot, last, lastId);
  }

  /** The cursor names a snapshot the order has not reached: it was not issued for this order. */
  static DomainException beyondOrder() {
    return invalid(CURSOR, "is not a position in this order's history");
  }

  private static UUID canonicalUuid(String text) {
    try {
      UUID value = UUID.fromString(text);
      if (!value.toString().equals(text)) {
        throw unreadable();
      }
      return value;
    } catch (IllegalArgumentException malformed) {
      throw unreadable();
    }
  }

  private static long nonNegative(String text) {
    if (text.isEmpty() || text.length() > 19 || !text.chars().allMatch(c -> c >= '0' && c <= '9')) {
      throw unreadable();
    }
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException overflow) {
      throw unreadable();
    }
  }

  private static DomainException unreadable() {
    return invalid(CURSOR, "is not a field-history cursor");
  }

  private static DomainException invalid(String field, String message) {
    return OrderDomainException.invalid("VALIDATION_ERROR", field + " " + message)
        .withFieldError(field, message);
  }
}
