package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.sales.orderintake.dto.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DraftStockSelectionLifecycleIT extends DraftStockSelectionITSupport {
  @Autowired LiveEditLeaseService leases;

  @Test
  void cancellationReopensRollsAndAnotherTenantCannotReadTheSelection() {
    var held = acquire(open(actor), actor);
    assertThat(in(tenant, () -> leases.held(StockUnitLeaseKeys.resource(product), 1))).hasSize(7);
    assertThatThrownBy(() -> in(otherTenant, () -> service.status(held.selectionId(), actor)))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderIntakeException.class);
    var release =
        new DraftStockSelectionRequests.Tokens(
            held.selectionRevision(),
            held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList());
    in(
        tenant,
        () -> {
          service.release(held.selectionId(), release, actor);
          return null;
        });
    assertThat(in(tenant, () -> leases.held(StockUnitLeaseKeys.resource(product), 1))).isEmpty();
    assertThat(acquire(open(otherActor), otherActor).grants()).hasSize(7);
  }

  @Test
  void explicitlyClosingTheFormEndsOwnershipAndItsSharedSession() {
    var held = acquire(open(actor), actor);
    in(
        tenant,
        () -> {
          service.close(
              held.selectionId(),
              new DraftStockSelectionRequests.Close(
                  held.selectionRevision(),
                  held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList()),
              actor);
          return null;
        });
    assertThat(in(tenant, () -> leases.held(StockUnitLeaseKeys.resource(product), 1))).isEmpty();
    assertThat(in(tenant, () -> service.status(held.selectionId(), actor)).state())
        .isEqualTo(DraftStockSelectionDto.State.RELEASED);
  }

  @Test
  void expiredLeaseReopensStockBeforeAnyCleanupJobRuns() {
    var held = acquire(open(actor), actor);
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "update common_infrastructure.live_edit_lease set acquired_at = now() - interval '3 minutes', renewed_at = now() - interval '2 minutes', expires_at = now() - interval '1 second' where tenant_id = ? and session_id = ?",
              tenant,
              held.sessionId());
          return null;
        });
    assertThat(in(tenant, () -> service.status(held.selectionId(), actor)).state())
        .isEqualTo(DraftStockSelectionDto.State.EXPIRED);
    assertThat(acquire(open(otherActor), otherActor).grants()).hasSize(7);
  }

  @Test
  void anExpiredPeriodCanBeReacquiredWithFreshTokensWithoutRevealingObsoleteOnes() {
    var held = acquire(open(actor), actor);
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "update common_infrastructure.live_edit_lease set acquired_at = now() - interval '3 minutes', renewed_at = now() - interval '2 minutes', expires_at = now() - interval '1 second' where tenant_id = ? and session_id = ?",
              tenant,
              held.sessionId());
          return null;
        });
    var expired = in(tenant, () -> service.status(held.selectionId(), actor));
    assertThat(expired.grants()).isEmpty();
    var refreshed = acquire(expired, actor);
    assertThat(refreshed.state()).isEqualTo(DraftStockSelectionDto.State.HELD);
    assertThat(refreshed.grants().stream().map(DraftStockSelectionDto.Grant::token).toList())
        .doesNotContainAnyElementsOf(
            held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList());
    in(
        tenant,
        () -> {
          service.release(
              held.selectionId(),
              new DraftStockSelectionRequests.Tokens(
                  held.selectionRevision(),
                  held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList()),
              actor);
          return null;
        });
    assertThat(in(tenant, () -> service.status(refreshed.selectionId(), actor)).state())
        .isEqualTo(DraftStockSelectionDto.State.HELD);
  }

  @Test
  void retryingAnAcquisitionDoesNotCreateAnotherOwnershipPeriod() {
    var session = open(actor);
    UUID operation = UUID.randomUUID();
    var command = command(session, operation);
    var first = in(tenant, () -> service.acquire(session.selectionId(), command, actor));
    var retry = in(tenant, () -> service.acquire(session.selectionId(), command, actor));
    assertThat(retry.grants()).isEqualTo(first.grants());
    assertThat(in(tenant, () -> leases.held(StockUnitLeaseKeys.resource(product), 1))).hasSize(7);
  }

  @Test
  void ownProofDoesNotConsumeItsStockTwiceButOtherMarketersSeeDisabledRolls() {
    var held = acquire(open(actor), actor);
    var original = request();
    var own =
        new OrderIntakeStockPreviewRequest(
            original.productId(),
            original.colorId(),
            original.finishedWidth(),
            original.finishedWidthUnit(),
            original.customerId(),
            original.requestedQty(),
            original.unit(),
            original.singleLotRequired(),
            original.toleranceUpPct(),
            original.toleranceDownPct(),
            null,
            new DraftStockSelectionRequests.Proof(
                held.selectionId(),
                held.selectionRevision(),
                held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList(),
                false));
    var mine = in(tenant, () -> previews.preview(own, actor));
    var theirs = in(tenant, () -> previews.preview(original, otherActor));
    assertThat(mine.totals().free().canonical()).isEqualByComparingTo("1200");
    assertThat(theirs.totals().free().canonical()).isEqualByComparingTo("500");
    assertThat(
            theirs.lots().stream()
                .flatMap(lot -> lot.pieces().stream())
                .filter(piece -> piece.held())
                .toList())
        .hasSize(7)
        .allMatch(
            piece ->
                piece.ownerType()
                    == com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption
                        .StockOwnerType.DRAFT);
    assertThatThrownBy(() -> in(tenant, () -> previews.preview(own, otherActor)))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderIntakeException.class);
  }

  @Test
  void renewingOwnershipDoesNotStaleAnAlreadyAppliedSelectionRevision() {
    var held = acquire(open(actor), actor);
    var renewed =
        in(
            tenant,
            () ->
                service.renew(
                    held.selectionId(),
                    new DraftStockSelectionRequests.Tokens(
                        held.selectionRevision(),
                        held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList()),
                    actor));
    assertThat(renewed.selectionRevision()).isEqualTo(held.selectionRevision());
    assertThat(renewed.grants().stream().map(DraftStockSelectionDto.Grant::token).toList())
        .isEqualTo(held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList());
  }
}
