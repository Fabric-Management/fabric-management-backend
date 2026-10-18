package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptedStockCheckTest {
  @Test
  void singleLotCannotBeOverriddenByTechnicalOrToneConfirmation() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var stock =
        new QuantityEvaluationService.LineStock(
            PrimaryMeasure.LENGTH, List.of(), List.of(Set.of(a, b)), List.of(Set.of(a, b)), false);
    assertThat(AcceptedStockCheck.compatibility(stock, Set.of(a, b)))
        .isEqualTo(Compatibility.PENDING_CONFIRMATION);
    assertThat(AcceptedStockCheck.compatibility(stock, Set.of(a)))
        .isEqualTo(Compatibility.SINGLE_LOT);
    var unrestricted =
        new QuantityEvaluationService.LineStock(
            PrimaryMeasure.LENGTH, List.of(), List.of(), List.of(), true);
    assertThat(AcceptedStockCheck.compatibility(unrestricted, Set.of(a, b)))
        .isEqualTo(Compatibility.MULTIPLE_LOTS_ACCEPTED);
  }
}
