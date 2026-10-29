package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectionCommandService {
  private final CollectionPostingService posting;
  private final CollectionChequeService cheques;
  private final CollectionOverSettlementService overSettlements;
  private final CollectionRefundService refunds;

  public CollectionDtos.Result match(UUID id, CollectionRequests.Match body, UUID actor) {
    return CollectionQueryService.result(posting.match(id, body, actor));
  }

  public CollectionDtos.Result receive(UUID id, CollectionRequests.Receipt body, UUID actor) {
    return CollectionQueryService.result(posting.receive(id, body, actor));
  }

  public CollectionDtos.Result reverse(UUID id, CollectionRequests.Reverse body, UUID actor) {
    return CollectionQueryService.result(posting.reverse(id, body, actor));
  }

  public CollectionDtos.Result receiveCheque(CollectionRequests.ReceiveCheque body, UUID actor) {
    return CollectionQueryService.result(cheques.receive(body, actor));
  }

  public CollectionDtos.Result transition(UUID id, CollectionRequests.Transition body, UUID actor) {
    return CollectionQueryService.result(cheques.transition(id, body, actor));
  }

  public CollectionDtos.Result resolveOverSettlement(
      UUID id, CollectionRequests.ResolveOverSettlement body, UUID actor) {
    return CollectionQueryService.result(overSettlements.resolve(id, body, actor));
  }

  public CollectionDtos.Result settleRefund(
      UUID id, CollectionRequests.SettleRefund body, UUID actor) {
    return CollectionQueryService.result(refunds.settle(id, body, actor));
  }

  public CollectionDtos.Result cancelRefund(
      UUID id, CollectionRequests.CancelRefund body, UUID actor) {
    return CollectionQueryService.result(refunds.cancel(id, body, actor));
  }
}
