package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.finance.collection.app.port.CollectionChequeDrawerPort;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.dto.CollectionChequeDrawerDtos;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The cheque form's drawer list and its one-step additions (PAYMENT-PLAN-7 R15). */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionChequeDrawerService {

  private final CollectionChequeDrawerPort drawers;

  @Transactional(readOnly = true)
  public CollectionChequeDrawerDtos.Drawers drawers(UUID partnerId) {
    var customer = drawers.customer(partnerId);
    return new CollectionChequeDrawerDtos.Drawers(
        partnerId,
        customer.name(),
        defaultKind(customer.defaultOwnChequePercent()),
        customer.activeDrawers().stream().map(CollectionChequeDrawerService::dto).toList());
  }

  public CollectionChequeDrawerDtos.AddResult add(
      UUID partnerId, CollectionChequeDrawerDtos.AddRequest request) {
    var added = drawers.add(partnerId, request.name(), request.drawerType());
    return new CollectionChequeDrawerDtos.AddResult(
        CollectionChequeDrawerDtos.AddOutcome.valueOf(added.outcome().name()), dto(added.drawer()));
  }

  public CollectionChequeDrawerDtos.Drawer reactivate(UUID partnerId, UUID drawerId) {
    return dto(drawers.reactivate(partnerId, drawerId));
  }

  /** A customer agreed to pay only by endorsed cheques starts a new cheque on "Endorsed". */
  static DrawerKind defaultKind(int defaultOwnChequePercent) {
    return defaultOwnChequePercent == 0 ? DrawerKind.ENDORSED : DrawerKind.OWN;
  }

  private static CollectionChequeDrawerDtos.Drawer dto(CollectionChequeDrawerPort.Drawer drawer) {
    return new CollectionChequeDrawerDtos.Drawer(
        drawer.id(), drawer.name(), drawer.type(), drawer.active());
  }
}
