package com.fabricmanagement.finance.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundAction;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundState;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CollectionRefundStateTest {
  @ParameterizedTest
  @CsvSource({
    "OPEN,SETTLE,SETTLED",
    "OPEN,CANCEL,CANCELLED",
    "SETTLED,REOPEN,OPEN",
  })
  void everyPermittedActionMovesToTheStateItNames(
      RefundState from, RefundAction action, RefundState to) {
    assertThat(from.permits(action)).isTrue();
    assertThat(from.next(action)).isEqualTo(to);
  }

  @ParameterizedTest
  @CsvSource({
    "OPEN,REOPEN",
    "SETTLED,SETTLE",
    "SETTLED,CANCEL",
    "CANCELLED,SETTLE",
    "CANCELLED,REOPEN",
    "CANCELLED,CANCEL",
  })
  void everyOtherActionIsRefusedAsAConflict(RefundState from, RefundAction action) {
    assertThat(from.permits(action)).isFalse();
    assertThatThrownBy(() -> from.next(action))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("REFUND_TRANSITION_NOT_ALLOWED");
              assertThat(e.getHttpStatus()).isEqualTo(409);
            });
  }

  @Test
  void aPaidRefundThatIsReopenedCanBePaidOrCancelledAgain() {
    var reopened = RefundState.OPEN.next(RefundAction.SETTLE).next(RefundAction.REOPEN);
    assertThat(reopened).isEqualTo(RefundState.OPEN);
    assertThat(reopened.permits(RefundAction.SETTLE)).isTrue();
    assertThat(reopened.permits(RefundAction.CANCEL)).isTrue();
  }
}
