package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.finance.collection.app.port.CollectionChequeDrawerPort;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns what was sent about a cheque's drawer into the recorded classification (PAYMENT-PLAN-7
 * R16). Kind and name are written when the cheque is received and never inferred later: an own
 * drawer's name is written here from the customer or its listed drawer, an endorsed one's name is
 * taken as typed. Name equality alone never establishes identity.
 */
@Component
@RequiredArgsConstructor
class CollectionChequeDrawerResolver {

  private final CollectionChequeDrawerPort drawers;

  /** At receipt a kind is always required. */
  CollectionCheque.Drawer forReceipt(CollectionRequests.ReceiveCheque request) {
    if (request.drawerKind() == null) throw kindRequired();
    return resolve(
        request.partnerId(), request.drawerKind(), request.drawerId(), request.drawerName(), null);
  }

  /**
   * A correction that sends none of the three fields keeps the recorded classification, an unknown
   * one included; null is returned. Sending the same own kind and drawer keeps the recorded name,
   * even when that drawer was since renamed or deactivated; choosing another drawer needs an active
   * one of this customer.
   */
  CollectionCheque.Drawer forCorrection(
      CollectionCheque cheque, CollectionRequests.Correction correction) {
    if (correction.keepsClassification()) return null;
    if (correction.drawerKind() == null) throw kindRequired();
    return resolve(
        cheque.getPartnerId(),
        correction.drawerKind(),
        correction.drawerId(),
        correction.drawerName(),
        cheque.drawer().orElse(null));
  }

  private CollectionCheque.Drawer resolve(
      UUID partnerId,
      DrawerKind kind,
      UUID drawerId,
      String drawerName,
      CollectionCheque.Drawer recorded) {
    return switch (kind) {
      case ENDORSED -> {
        if (drawerId != null)
          throw invalid("An endorsed cheque's drawer is not one of the customer's listed drawers");
        String name = tidy(drawerName);
        if (name == null)
          throw CollectionRuleException.invalid(
              "CHEQUE_DRAWER_NAME_REQUIRED", "Enter the name of the endorsed cheque's drawer");
        yield new CollectionCheque.Drawer(DrawerKind.ENDORSED, null, name);
      }
      case OWN -> {
        if (drawerName != null)
          throw invalid("An own cheque's drawer name is written from the customer's records");
        if (recorded != null
            && recorded.kind() == DrawerKind.OWN
            && Objects.equals(recorded.id(), drawerId)) yield recorded;
        if (drawerId == null)
          yield new CollectionCheque.Drawer(
              DrawerKind.OWN,
              null,
              drawers
                  .customerName(partnerId)
                  .orElseThrow(() -> invalid("The customer is not known in this company")));
        var listed =
            drawers
                .drawer(partnerId, drawerId)
                .filter(CollectionChequeDrawerPort.Drawer::active)
                .orElseThrow(() -> invalid("Choose an active cheque drawer of this customer"));
        yield new CollectionCheque.Drawer(DrawerKind.OWN, listed.id(), listed.name());
      }
    };
  }

  /**
   * The display form of a typed name, as the drawer list writes it: NFKC, every run of spaces (the
   * non-breaking one included) collapsed, then trimmed. Blank means no name.
   */
  private static String tidy(String value) {
    if (value == null) return null;
    String shown =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
            .replaceAll("[\\s\\p{Z}]+", " ")
            .strip();
    return shown.isEmpty() ? null : shown;
  }

  private static CollectionRuleException kindRequired() {
    return CollectionRuleException.invalid(
        "CHEQUE_DRAWER_KIND_REQUIRED", "Choose whether this is an own or an endorsed cheque");
  }

  private static CollectionRuleException invalid(String message) {
    return CollectionRuleException.invalid("CHEQUE_DRAWER_INVALID", message);
  }
}
