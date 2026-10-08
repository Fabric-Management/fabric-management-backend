package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.LineOperation;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditInstructions.Parsed;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseField;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseKey;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The sales order's lease catalogue on the platform's keys (CEDIT-07 §3.2). The lease unit is the
 * safe-edit key: a composite key (contact, delivery terms, pricing, quantity, tolerance,
 * specification …) is one lease and its parts never are. Header keys share the scope {@code
 * header}; each line is its own scope {@code line:<lineId>}, with {@code *} for the whole line. The
 * whole line overlaps every key of that line, so removing a line and changing one of its fields
 * exclude each other; different lines and different header keys never collide. A line not yet saved
 * (ADD with a client line id) is nobody else's yet and needs no lease. Pure.
 */
public final class SalesOrderLeaseKeys {

  static final String HEADER_SCOPE = "header";
  private static final String LINE_SCOPE_PREFIX = "line:";

  private SalesOrderLeaseKeys() {}

  /** The platform key of a safe-edit key; {@code lineId} for line keys only. */
  public static LiveLeaseKey of(OrderEditKey key, UUID lineId) {
    if (key.isLineKey()) {
      if (lineId == null) {
        throw new IllegalArgumentException("A line key names its line: " + key.wireName());
      }
      return new LiveLeaseKey(lineScope(lineId), key.wireName());
    }
    if (lineId != null) {
      throw new IllegalArgumentException("A header key names no line: " + key.wireName());
    }
    return new LiveLeaseKey(HEADER_SCOPE, key.wireName());
  }

  /** The key of a whole line: what removing it needs. */
  public static LiveLeaseKey wholeLine(UUID lineId) {
    return LiveLeaseKey.whole(lineScope(lineId));
  }

  /** The platform key of a wire key the caller already checked for its line id. */
  static LiveLeaseKey of(SalesOrderEditLeaseKey key) {
    return key.key()
        .editKey()
        .map(edit -> of(edit, key.lineId()))
        .orElseGet(() -> wholeLine(key.lineId()));
  }

  /** The wire key of a platform key of this catalogue. */
  static SalesOrderEditLeaseKey toWire(LiveLeaseKey key) {
    if (HEADER_SCOPE.equals(key.scope())) {
      return new SalesOrderEditLeaseKey(SalesOrderEditLeaseField.fromWireName(key.part()), null);
    }
    if (!key.scope().startsWith(LINE_SCOPE_PREFIX)) {
      throw new IllegalArgumentException("Not a sales order lease scope: " + key.scope());
    }
    UUID lineId = UUID.fromString(key.scope().substring(LINE_SCOPE_PREFIX.length()));
    SalesOrderEditLeaseField field =
        key.isWhole()
            ? SalesOrderEditLeaseField.LINE
            : SalesOrderEditLeaseField.fromWireName(key.part());
    return new SalesOrderEditLeaseKey(field, lineId);
  }

  /**
   * The keys a save writes, from what it sends (CEDIT-07 §3.2 table): every header instruction,
   * every field instruction of a line UPDATE, the whole line of a REMOVE; an ADD nothing. The
   * request's intent decides, not whether the value would differ in the database: a SET to the
   * value the key already has still needs the key. KEEP_CURRENT sends no instruction and needs
   * nothing; USE_MINE and NEW_VALUE send one and need its key; a decision carried past an earlier
   * conflict is sent again and needs its key the same way. A resolution alone needs no lease.
   */
  static Set<LiveLeaseKey> required(Parsed parsed) {
    Set<LiveLeaseKey> keys = new TreeSet<>();
    parsed.header().keySet().forEach(key -> keys.add(of(key, null)));
    for (LineOperation line : parsed.lines()) {
      switch (line.operation()) {
        case UPDATE -> line.fields().keySet().forEach(key -> keys.add(of(key, line.lineId())));
        case REMOVE -> keys.add(wholeLine(line.lineId()));
        case ADD -> {
          // A new line is not shared before it is saved: no lease.
        }
      }
    }
    return Collections.unmodifiableSet(keys);
  }

  private static String lineScope(UUID lineId) {
    return LINE_SCOPE_PREFIX + lineId;
  }
}
