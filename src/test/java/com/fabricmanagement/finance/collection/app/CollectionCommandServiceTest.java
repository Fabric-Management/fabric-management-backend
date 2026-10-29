package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The facade only routes: each command must reach its own service and come back as a result. */
class CollectionCommandServiceTest {
  final UUID actor = UUID.randomUUID(), id = UUID.randomUUID();
  final CollectionPostingService posting = mock(CollectionPostingService.class);
  final CollectionChequeService cheques = mock(CollectionChequeService.class);
  final CollectionOverSettlementService overSettlements =
      mock(CollectionOverSettlementService.class);
  final CollectionRefundService refunds = mock(CollectionRefundService.class);
  final CollectionCommandService service =
      new CollectionCommandService(posting, cheques, overSettlements, refunds);

  private CollectionCommandReceipt receipt(String type, UUID payment, UUID... matches) {
    return CollectionCommandReceipt.create(
        UUID.randomUUID(), actor, "hash", type, id, 7L, payment, List.of(matches));
  }

  private void assertResult(
      CollectionDtos.Result result, CollectionCommandReceipt receipt, String type) {
    assertThat(result.operationId()).isEqualTo(receipt.getOperationId());
    assertThat(result.resourceType()).isEqualTo(type);
    assertThat(result.resourceId()).isEqualTo(id);
    assertThat(result.resourceVersion()).isEqualTo(7L);
    assertThat(result.paymentId()).isEqualTo(receipt.getPaymentId());
    assertThat(result.matchIds()).isEqualTo(receipt.getMatchIds());
  }

  @Test
  void match_goesToPostingAndReturnsTheMatchesItMade() {
    var body = mock(CollectionRequests.Match.class);
    var made = receipt("OBLIGATION", UUID.randomUUID(), UUID.randomUUID());
    when(posting.match(id, body, actor)).thenReturn(made);

    assertResult(service.match(id, body, actor), made, "OBLIGATION");
    verifyNoMoreInteractions(cheques, overSettlements, refunds);
  }

  @Test
  void receive_goesToPostingAndReturnsThePaymentItRecorded() {
    var body = mock(CollectionRequests.Receipt.class);
    var made = receipt("OBLIGATION", UUID.randomUUID(), UUID.randomUUID());
    when(posting.receive(id, body, actor)).thenReturn(made);

    assertResult(service.receive(id, body, actor), made, "OBLIGATION");
    verifyNoMoreInteractions(cheques, overSettlements, refunds);
  }

  @Test
  void reverse_goesToPostingAndKeepsAnEmptyMatchListEmpty() {
    var body = mock(CollectionRequests.Reverse.class);
    var made = receipt("OBLIGATION", null);
    when(posting.reverse(id, body, actor)).thenReturn(made);

    var result = service.reverse(id, body, actor);

    assertResult(result, made, "OBLIGATION");
    assertThat(result.paymentId()).isNull();
    assertThat(result.matchIds()).isEmpty();
    verifyNoMoreInteractions(cheques, overSettlements, refunds);
  }

  @Test
  void receiveCheque_goesToTheChequeServiceWithoutATarget() {
    var body = mock(CollectionRequests.ReceiveCheque.class);
    var made = receipt("CHEQUE", null);
    when(cheques.receive(body, actor)).thenReturn(made);

    assertResult(service.receiveCheque(body, actor), made, "CHEQUE");
    verifyNoMoreInteractions(posting, overSettlements, refunds);
  }

  @Test
  void transition_goesToTheChequeServiceForThatCheque() {
    var body = mock(CollectionRequests.Transition.class);
    var made = receipt("CHEQUE", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(cheques.transition(id, body, actor)).thenReturn(made);

    var result = service.transition(id, body, actor);

    assertResult(result, made, "CHEQUE");
    assertThat(result.matchIds()).hasSize(2);
    verifyNoMoreInteractions(posting, overSettlements, refunds);
  }

  @Test
  void resolveOverSettlement_goesToTheOverSettlementService() {
    var body = mock(CollectionRequests.ResolveOverSettlement.class);
    var made = receipt("OBLIGATION", UUID.randomUUID());
    when(overSettlements.resolve(id, body, actor)).thenReturn(made);

    assertResult(service.resolveOverSettlement(id, body, actor), made, "OBLIGATION");
    verifyNoMoreInteractions(posting, cheques, refunds);
  }

  @Test
  void settleRefund_goesToTheRefundService() {
    var body = mock(CollectionRequests.SettleRefund.class);
    var made = receipt("OBLIGATION", UUID.randomUUID());
    when(refunds.settle(id, body, actor)).thenReturn(made);

    assertResult(service.settleRefund(id, body, actor), made, "OBLIGATION");
    verify(refunds).settle(id, body, actor);
    verifyNoMoreInteractions(posting, cheques, overSettlements);
  }

  @Test
  void cancelRefund_goesToTheRefundService() {
    var body = mock(CollectionRequests.CancelRefund.class);
    var made = receipt("OBLIGATION", null);
    when(refunds.cancel(id, body, actor)).thenReturn(made);

    assertResult(service.cancelRefund(id, body, actor), made, "OBLIGATION");
    verify(refunds).cancel(id, body, actor);
    verifyNoMoreInteractions(posting, cheques, overSettlements);
  }
}
