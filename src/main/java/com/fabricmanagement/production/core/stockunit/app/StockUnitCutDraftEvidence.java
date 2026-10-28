package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanPort.DraftInput;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.Context;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutSessionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stores a cut preview beside the existing draft leases without changing their selection revision.
 */
@Component
@RequiredArgsConstructor
public class StockUnitCutDraftEvidence {
  private final StockUnitCutSessionRepository sessions;
  private final StockUnitCutPlanOperations operations;
  private final LiveEditLeaseService leases;

  public void remember(
      DraftInput input,
      BigDecimal quantity,
      List<StockUnitCutPlan.Source> sources,
      UUID cut,
      UUID actor) {
    var row =
        sessions
            .findByTenantIdAndSessionId(TenantContext.requireTenantId(), input.sessionId())
            .orElseGet(
                () ->
                    StockUnitCutSession.open(
                        input.sessionId(),
                        actor,
                        "DRAFT_LINE",
                        input.formId(),
                        input.ownLineId() == null ? input.clientLineId() : input.ownLineId(),
                        input.productId(),
                        tokenHash(input.productId(), input.sessionId(), actor)));
    if (!row.getActorId().equals(actor)
        || !row.getTargetType().equals("DRAFT_LINE")
        || row.isEnded()) throw changed();
    row.bindDraftEvidence(tokenHash(input.productId(), input.sessionId(), actor));
    row.remember(
        input.sources(),
        cut,
        operations.hash(List.of(quantity.stripTrailingZeros().toPlainString(), sources)));
    sessions.saveAndFlush(row);
  }

  public void assertCurrent(
      Context target, List<StockUnitCutPlan.Source> sources, UUID cut, UUID session, UUID actor) {
    var row =
        sessions
            .findByTenantIdAndSessionId(TenantContext.requireTenantId(), session)
            .orElseThrow(this::changed);
    if (row.isEnded()
        || !row.getActorId().equals(actor)
        || !row.getProductId().equals(target.productId())
        || !Objects.equals(row.getCutSourceId(), cut)) throw changed();
    String expected;
    if ("DRAFT_LINE".equals(row.getTargetType())) {
      if (!row.getTargetFingerprint().equals(tokenHash(target.productId(), session, actor)))
        throw changed();
      expected =
          operations.hash(List.of(target.quantity().stripTrailingZeros().toPlainString(), sources));
    } else {
      if (!row.getTargetType().equals(target.target().type().name())
          || !row.getTargetId().equals(target.target().documentId())
          || !row.getTargetLineId().equals(target.target().lineId())) throw changed();
      expected = operations.hash(List.of(target.fingerprint(), sources));
    }
    if (!expected.equals(row.getEvidenceHash())) throw changed();
  }

  private String tokenHash(UUID product, UUID session, UUID actor) {
    var tokens =
        leases.held(StockUnitLeaseKeys.resource(product), StockUnitLeaseKeys.GENERATION).stream()
            .filter(
                lease -> lease.getSessionId().equals(session) && lease.getUserId().equals(actor))
            .map(com.fabricmanagement.platform.realtime.domain.LiveEditLease::getToken)
            .sorted()
            .toList();
    if (tokens.isEmpty()) throw changed();
    return operations.hash(tokens);
  }

  private StockUnitCutPlanException changed() {
    return StockUnitCutPlanException.conflict(
        "EVALUATION_REQUIRED",
        "Evaluate the current cut source measurements and ownership period before applying");
  }
}
