package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionRequests;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read-only own-stock exemption. Actor, revision and complete secret periods are verified. */
@Component
@RequiredArgsConstructor
public class DraftStockReadContext {
  private final DraftStockSelectionRepository selections;
  private final LiveEditLeaseService leases;

  public record Own(UUID sessionId, UUID targetLineId) {}

  public Own verify(UUID productId, DraftStockSelectionRequests.Proof proof, UUID actor) {
    var row =
        selections
            .findByTenantIdAndId(TenantContext.requireTenantId(), proof.selectionId())
            .filter(
                value -> value.getActorId().equals(actor) && value.getProductId().equals(productId))
            .orElseThrow(
                () -> OrderIntakeException.notFound("Draft stock selection", proof.selectionId()));
    var tokens = Set.copyOf(proof.tokens());
    var actual =
        leases.held(StockUnitLeaseKeys.resource(productId), 1).stream()
            .filter(lease -> lease.getSessionId().equals(row.getSessionId()))
            .map(com.fabricmanagement.platform.realtime.domain.LiveEditLease::getToken)
            .collect(Collectors.toSet());
    if (row.getState() != DraftStockSelection.State.HELD
        || !Objects.equals(row.getVersion(), proof.selectionRevision())
        || tokens.isEmpty()
        || tokens.size() != proof.tokens().size()
        || !tokens.equals(Set.copyOf(row.getTokens()))
        || !tokens.equals(actual))
      throw OrderIntakeException.conflict(
              "STOCK_PROPOSAL_STALE", "The preview no longer holds this exact stock selection")
          .withDetail("clientLineId", row.getClientLineId())
          .withDetail("selectionId", row.getId());
    return new Own(row.getSessionId(), row.getTargetLineId());
  }
}
