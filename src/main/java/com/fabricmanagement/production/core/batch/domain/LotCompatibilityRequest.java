package com.fabricmanagement.production.core.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * A request that a competent person decides whether named lots may ship together (SOI A04). Sales
 * opens it when a customer answered a multi-lot option before compatibility was known; it is closed
 * by a confirmation, a decline or a withdrawal. Asking never counts as the answer.
 */
@Entity
@Table(name = "lot_compatibility_request", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotCompatibilityRequest extends BaseEntity {

  @Type(JsonType.class)
  @Column(name = "batch_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> batchIds = new ArrayList<>();

  @Column(name = "batch_key", nullable = false, updatable = false, length = 800)
  private String batchKey;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "customer_id", updatable = false)
  private UUID customerId;

  @Column(name = "source_type", nullable = false, updatable = false, length = 40)
  private String sourceType;

  @Column(name = "source_id", nullable = false, updatable = false)
  private UUID sourceId;

  @Column(name = "note", updatable = false, columnDefinition = "TEXT")
  private String note;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private LotCompatibilityRequestStatus status;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolution_note", columnDefinition = "TEXT")
  private String resolutionNote;

  @Column(name = "confirmation_id")
  private UUID confirmationId;

  public static LotCompatibilityRequest open(
      Collection<UUID> batchIds,
      UUID productId,
      UUID customerId,
      String sourceType,
      UUID sourceId,
      String note,
      UUID requestedBy,
      Instant requestedAt) {
    Set<UUID> distinct = batchIds == null ? Set.of() : Set.copyOf(batchIds);
    if (distinct.size() < 2) {
      throw new IllegalArgumentException("A compatibility request names at least two lots");
    }
    if (productId == null || sourceType == null || sourceId == null) {
      throw new IllegalArgumentException("Product and source are required");
    }
    if (requestedBy == null || requestedAt == null) {
      throw new IllegalArgumentException("Requester and time are required");
    }
    LotCompatibilityRequest request = new LotCompatibilityRequest();
    request.batchIds = distinct.stream().sorted().toList();
    request.batchKey = key(distinct);
    request.productId = productId;
    request.customerId = customerId;
    request.sourceType = sourceType;
    request.sourceId = sourceId;
    request.note = note == null || note.isBlank() ? null : note.trim();
    request.requestedBy = requestedBy;
    request.requestedAt = requestedAt;
    request.status = LotCompatibilityRequestStatus.OPEN;
    return request;
  }

  public static String key(Collection<UUID> batchIds) {
    return batchIds.stream()
        .distinct()
        .sorted()
        .map(UUID::toString)
        .collect(Collectors.joining(","));
  }

  /** True when the confirmation covers every lot of the request for its customer. */
  public boolean isAnsweredBy(LotCompatibilityConfirmation confirmation) {
    return status == LotCompatibilityRequestStatus.OPEN
        && confirmation.covers(batchIds, customerId);
  }

  public void confirmedBy(LotCompatibilityConfirmation confirmation) {
    requireOpen();
    this.status = LotCompatibilityRequestStatus.CONFIRMED;
    this.confirmationId = confirmation.getId();
    this.resolvedBy = confirmation.getConfirmedBy();
    this.resolvedAt = confirmation.getConfirmedAt();
  }

  public void decline(UUID actor, Instant at, String reason) {
    requireOpen();
    if (actor == null || at == null || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Actor, time and reason are required to decline");
    }
    this.status = LotCompatibilityRequestStatus.DECLINED;
    this.resolvedBy = actor;
    this.resolvedAt = at;
    this.resolutionNote = reason.trim();
  }

  public void withdraw(UUID actor, Instant at) {
    requireOpen();
    this.status = LotCompatibilityRequestStatus.WITHDRAWN;
    this.resolvedBy = actor;
    this.resolvedAt = at;
  }

  private void requireOpen() {
    if (status != LotCompatibilityRequestStatus.OPEN) {
      throw new IllegalStateException("The compatibility request is already " + status);
    }
  }

  @Override
  protected String getModuleCode() {
    return "LCR";
  }
}
