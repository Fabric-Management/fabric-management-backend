package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/** Business association of a form line and common leases; lease ownership stays in platform. */
@Entity
@Table(name = "draft_stock_selection", schema = "sales_ord")
@Getter
@NoArgsConstructor
public class DraftStockSelection extends BaseEntity {
  public enum State {
    OPEN,
    HELD,
    RELEASED,
    TRANSFERRED,
    CONVERTED,
    SUPERSEDED
  }

  @Column(nullable = false)
  private UUID formInstanceId;

  @Column(nullable = false)
  private UUID clientLineId;

  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private UUID actorId;

  @Column private UUID sessionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private State state = State.OPEN;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private OrderIntakeStockPreviewRequest request;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private QuantityOption option;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  private List<UUID> tokens = List.of();

  private Instant expiresAt;
  private UUID salesOrderId;
  private UUID salesOrderLineId;
  private String termsFingerprint;
  private Long orderVersion;
  private UUID targetOrderId;
  private UUID targetLineId;
  private UUID sourceQuoteId;
  private UUID sourceQuoteLineId;

  public static DraftStockSelection open(
      UUID form, UUID line, UUID product, UUID actor, UUID session) {
    DraftStockSelection value = new DraftStockSelection();
    value.formInstanceId = form;
    value.clientLineId = line;
    value.productId = product;
    value.actorId = actor;
    value.sessionId = session;
    return value;
  }

  public void sourceQuote(UUID quoteId, UUID quoteLineId) {
    this.sourceQuoteId = quoteId;
    this.sourceQuoteLineId = quoteLineId;
  }

  public void bindTarget(UUID orderId, UUID lineId) {
    this.targetOrderId = orderId;
    this.targetLineId = lineId;
  }

  public void reopenSession(UUID sessionId) {
    this.sessionId = sessionId;
    this.tokens = List.of();
    this.state = State.OPEN;
  }

  public void hold(
      OrderIntakeStockPreviewRequest request,
      QuantityOption option,
      List<UUID> tokens,
      Instant expiry) {
    this.request = request;
    this.option = option;
    this.tokens = List.copyOf(tokens);
    this.expiresAt = expiry;
    this.state = State.HELD;
  }

  public void release() {
    this.state = State.RELEASED;
    this.tokens = List.of();
  }

  public void transfer(UUID orderId, UUID lineId, String terms) {
    this.salesOrderId = orderId;
    this.salesOrderLineId = lineId;
    this.termsFingerprint = terms;
    this.state = State.TRANSFERRED;
    this.tokens = List.of();
  }

  public void orderVersion(long version) {
    this.orderVersion = version;
  }

  public void converted() {
    this.state = State.CONVERTED;
  }

  public void supersede() {
    this.state = State.SUPERSEDED;
  }

  @Override
  public String getModuleCode() {
    return "DSEL";
  }
}
