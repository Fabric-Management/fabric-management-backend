package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/**
 * A server-owned, immutable edit base (CEDIT-02 §3): the edit projection one actor started from on
 * one order. A save names it; the client never sends base values. A base grants no lock and no
 * write right, and its expiry is fixed when it is created; using it never extends it.
 */
@Entity
@Immutable
@Table(name = "order_edit_base", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEditBase extends BaseEntity {

  /** How a base came to be. */
  public enum Origin {
    /** Taken when the edit form opened. */
    OPENED,
    /** The next base after a save that applied changes, or that had nothing to apply. */
    SAVED,
    /** The current state a conflicting save is resolved against. */
    CONFLICT,
    /** The current state a save against an expired base is reviewed against. */
    EXPIRED
  }

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "order_version", nullable = false, updatable = false)
  private long orderVersion;

  @Type(JsonType.class)
  @Column(name = "content", nullable = false, updatable = false, columnDefinition = "jsonb")
  private OrderEditSnapshot content;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, updatable = false, length = 20)
  private Origin origin;

  /** The base the request named; a logical reference, so a cleaned parent never breaks a child. */
  @Column(name = "parent_base_id", updatable = false)
  private UUID parentBaseId;

  /** The operation that produced this base; null for an opened base. */
  @Column(name = "origin_operation_id", updatable = false)
  private UUID originOperationId;

  /** Parent values of keys other writers changed before this base (§5.8), copied, not linked. */
  @Type(JsonType.class)
  @Column(name = "guard", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<GuardEntry> guard;

  @Column(name = "captured_at", nullable = false, updatable = false)
  private Instant capturedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  /** One guarded key: where it is and the comparable identity of the parent's value. */
  public record GuardEntry(String key, UUID lineId, String parentToken) {}

  /** The base of a newly opened edit form. */
  public static OrderEditBase opened(
      UUID salesOrderId, UUID actorId, OrderEditSnapshot content, Instant now, Duration ttl) {
    return create(salesOrderId, actorId, content, Origin.OPENED, null, null, Map.of(), now, ttl);
  }

  /** A base produced by a save, a conflict or an expired-base review. */
  public static OrderEditBase derived(
      UUID salesOrderId,
      UUID actorId,
      OrderEditSnapshot content,
      Origin origin,
      UUID parentBaseId,
      UUID originOperationId,
      Map<Slot, String> guard,
      Instant now,
      Duration ttl) {
    if (origin == Origin.OPENED || originOperationId == null) {
      throw new IllegalArgumentException("A derived base names the operation that produced it");
    }
    return create(
        salesOrderId, actorId, content, origin, parentBaseId, originOperationId, guard, now, ttl);
  }

  private static OrderEditBase create(
      UUID salesOrderId,
      UUID actorId,
      OrderEditSnapshot content,
      Origin origin,
      UUID parentBaseId,
      UUID originOperationId,
      Map<Slot, String> guard,
      Instant now,
      Duration ttl) {
    Objects.requireNonNull(salesOrderId, "Order is required");
    Objects.requireNonNull(actorId, "Actor is required");
    Objects.requireNonNull(content, "Content is required");
    Objects.requireNonNull(now, "Capture time is required");
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("A base lives for a positive time");
    }
    OrderEditBase base = new OrderEditBase();
    base.salesOrderId = salesOrderId;
    base.actorId = actorId;
    base.orderVersion = content.orderVersion();
    base.content = content;
    base.origin = origin;
    base.parentBaseId = parentBaseId;
    base.originOperationId = originOperationId;
    List<GuardEntry> entries = new ArrayList<>();
    guard.forEach((slot, token) -> entries.add(new GuardEntry(slot.key(), slot.lineId(), token)));
    base.guard = List.copyOf(entries);
    base.capturedAt = now;
    base.expiresAt = now.plus(ttl);
    return base;
  }

  /** Expiry is checked on every save; a base is never extended by being used. */
  public boolean isExpiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public Map<Slot, String> guardMap() {
    Map<Slot, String> map = new LinkedHashMap<>();
    if (guard != null) {
      guard.forEach(
          entry -> map.put(new Slot(entry.key(), entry.lineId(), null), entry.parentToken()));
    }
    return map;
  }

  /** A save may use the base only on its own order and by its own actor. */
  public boolean belongsTo(UUID orderId, UUID actor) {
    return salesOrderId.equals(orderId) && actorId.equals(actor);
  }

  @Override
  protected String getModuleCode() {
    return "OEB";
  }
}
