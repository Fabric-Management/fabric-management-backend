package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.app.port.CollectionChequeDrawerPort;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerType;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAYMENT-PLAN-7 R16: the receive and correct table for a cheque's drawer. */
class CollectionChequeDrawerResolverTest {

  private final UUID partner = UUID.randomUUID();
  private final UUID listedId = UUID.randomUUID();
  private final UUID retiredId = UUID.randomUUID();
  private final CollectionChequeDrawerPort port = mock(CollectionChequeDrawerPort.class);
  private final CollectionChequeDrawerResolver resolver = new CollectionChequeDrawerResolver(port);

  {
    when(port.customerName(partner)).thenReturn(Optional.of("Northbridge Cloth"));
    when(port.drawer(partner, listedId))
        .thenReturn(
            Optional.of(
                new CollectionChequeDrawerPort.Drawer(
                    listedId, "Oliver Hartley", DrawerType.INDIVIDUAL, true)));
    when(port.drawer(partner, retiredId))
        .thenReturn(
            Optional.of(
                new CollectionChequeDrawerPort.Drawer(
                    retiredId, "Ava Pryce", DrawerType.INDIVIDUAL, false)));
  }

  private CollectionRequests.ReceiveCheque receive(DrawerKind kind, UUID id, String name) {
    return new CollectionRequests.ReceiveCheque(
        UUID.randomUUID(),
        partner,
        "GBP",
        new BigDecimal("3000.00"),
        LocalDate.of(2026, 11, 15),
        LocalDate.of(2026, 12, 15),
        "CH-001",
        "Scan",
        Instant.parse("2026-11-15T10:00:00Z"),
        "Handed over",
        List.of(new CollectionRequests.Application(UUID.randomUUID(), 0L, BigDecimal.ONE)),
        kind,
        id,
        name);
  }

  private static String code(Runnable call) {
    try {
      call.run();
      return null;
    } catch (CollectionRuleException refused) {
      return refused.getErrorCode();
    }
  }

  @Test
  void ownWithoutADrawerIsTheCustomerItselfNamedByTheBackend() {
    var drawer = resolver.forReceipt(receive(DrawerKind.OWN, null, null));
    assertThat(drawer)
        .isEqualTo(new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth"));
  }

  @Test
  void ownWithAnActiveListedDrawerRecordsItsIdAndName() {
    var drawer = resolver.forReceipt(receive(DrawerKind.OWN, listedId, null));
    assertThat(drawer)
        .isEqualTo(new CollectionCheque.Drawer(DrawerKind.OWN, listedId, "Oliver Hartley"));
  }

  @Test
  void endorsedKeepsTheTypedNameEvenWhenItMatchesTheCustomer() {
    assertThat(resolver.forReceipt(receive(DrawerKind.ENDORSED, null, "  Pennine   Weaving ")))
        .isEqualTo(new CollectionCheque.Drawer(DrawerKind.ENDORSED, null, "Pennine Weaving"));
    assertThat(resolver.forReceipt(receive(DrawerKind.ENDORSED, null, "Northbridge Cloth")).kind())
        .isEqualTo(DrawerKind.ENDORSED);
  }

  /** PAYMENT-PLAN-9 C1: receipts come only from a customer of this company that was not removed. */
  @Test
  void receiptsAreTakenOnlyFromACustomerThatWasNotRemoved() {
    UUID other = UUID.randomUUID();
    when(port.standing(other)).thenReturn(CollectionChequeDrawerPort.CustomerStanding.NOT_FOUND);
    assertThat(code(() -> resolver.requireCustomer(other)))
        .isEqualTo("COLLECTION_PARTNER_NOT_FOUND");
    when(port.standing(other)).thenReturn(CollectionChequeDrawerPort.CustomerStanding.NOT_CUSTOMER);
    assertThat(code(() -> resolver.requireCustomer(other)))
        .isEqualTo("COLLECTION_PARTNER_NOT_CUSTOMER");
    when(port.standing(other)).thenReturn(CollectionChequeDrawerPort.CustomerStanding.REMOVED);
    assertThat(code(() -> resolver.requireCustomer(other))).isEqualTo("COLLECTION_PARTNER_REMOVED");
    when(port.standing(other)).thenReturn(CollectionChequeDrawerPort.CustomerStanding.CUSTOMER);
    assertThat(code(() -> resolver.requireCustomer(other))).isNull();
  }

  /**
   * The customer view answers 404 for anyone who is not a customer; a removed one stays readable.
   */
  @Test
  void theCustomerViewReadsCustomersRemovedOnesIncluded() {
    UUID other = UUID.randomUUID();
    for (var hidden :
        List.of(
            CollectionChequeDrawerPort.CustomerStanding.NOT_FOUND,
            CollectionChequeDrawerPort.CustomerStanding.NOT_CUSTOMER)) {
      when(port.standing(other)).thenReturn(hidden);
      assertThatThrownBy(() -> resolver.requireCustomerToRead(other))
          .isInstanceOf(NotFoundException.class);
    }
    for (var readable :
        List.of(
            CollectionChequeDrawerPort.CustomerStanding.REMOVED,
            CollectionChequeDrawerPort.CustomerStanding.CUSTOMER)) {
      when(port.standing(other)).thenReturn(readable);
      resolver.requireCustomerToRead(other);
    }
  }

  @Test
  void everyRefusalOfTheTableHasItsCode() {
    assertThat(code(() -> resolver.forReceipt(receive(null, null, null))))
        .isEqualTo("CHEQUE_DRAWER_KIND_REQUIRED");
    assertThat(code(() -> resolver.forReceipt(receive(DrawerKind.OWN, retiredId, null))))
        .isEqualTo("CHEQUE_DRAWER_INVALID");
    assertThat(code(() -> resolver.forReceipt(receive(DrawerKind.OWN, UUID.randomUUID(), null))))
        .isEqualTo("CHEQUE_DRAWER_INVALID");
    assertThat(code(() -> resolver.forReceipt(receive(DrawerKind.OWN, null, "Someone"))))
        .isEqualTo("CHEQUE_DRAWER_INVALID");
    assertThat(code(() -> resolver.forReceipt(receive(DrawerKind.ENDORSED, null, "   "))))
        .isEqualTo("CHEQUE_DRAWER_NAME_REQUIRED");
    assertThat(code(() -> resolver.forReceipt(receive(DrawerKind.ENDORSED, listedId, "X"))))
        .isEqualTo("CHEQUE_DRAWER_INVALID");
  }

  private CollectionCheque cheque(CollectionCheque.Drawer recorded) {
    return CollectionCheque.receive(
        partner,
        "GBP",
        new BigDecimal("3000.00"),
        LocalDate.of(2026, 11, 15),
        LocalDate.of(2026, 12, 15),
        "CH-001",
        "Scan",
        recorded);
  }

  private static CollectionRequests.Correction correction(DrawerKind kind, UUID id, String name) {
    return new CollectionRequests.Correction(
        new BigDecimal("3000.00"),
        LocalDate.of(2026, 11, 15),
        LocalDate.of(2027, 1, 15),
        "CH-001",
        kind,
        id,
        name);
  }

  @Test
  void anOrdinaryCorrectionKeepsTheRecordedClassification() {
    var recorded = new CollectionCheque.Drawer(DrawerKind.OWN, retiredId, "Ava Pryce");
    assertThat(resolver.forCorrection(cheque(recorded), correction(null, null, null))).isNull();
  }

  @Test
  void theSameOwnDrawerKeepsItsRecordedNameEvenAfterRenameOrDeactivation() {
    var recorded = new CollectionCheque.Drawer(DrawerKind.OWN, retiredId, "Ava Pryce (old)");
    assertThat(
            resolver.forCorrection(cheque(recorded), correction(DrawerKind.OWN, retiredId, null)))
        .isEqualTo(recorded);
    var customer = new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth Ltd");
    assertThat(resolver.forCorrection(cheque(customer), correction(DrawerKind.OWN, null, null)))
        .isEqualTo(customer);
  }

  @Test
  void aCorrectionCanChangeKindButNotPickAnotherInactiveDrawer() {
    var recorded = new CollectionCheque.Drawer(DrawerKind.OWN, listedId, "Oliver Hartley");
    assertThat(
            resolver.forCorrection(
                cheque(recorded), correction(DrawerKind.ENDORSED, null, "Calder Mills")))
        .isEqualTo(new CollectionCheque.Drawer(DrawerKind.ENDORSED, null, "Calder Mills"));
    assertThat(
            code(
                () ->
                    resolver.forCorrection(
                        cheque(recorded), correction(DrawerKind.OWN, retiredId, null))))
        .isEqualTo("CHEQUE_DRAWER_INVALID");
    assertThat(
            code(
                () ->
                    resolver.forCorrection(
                        cheque(recorded), correction(null, null, "Calder Mills"))))
        .isEqualTo("CHEQUE_DRAWER_KIND_REQUIRED");
  }

  @Test
  void classificationCorrectionRefreshesAnActiveOwnDrawerButKeepsAnInactiveSnapshot() {
    var active = new CollectionCheque.Drawer(DrawerKind.OWN, listedId, "Oliver J Hartley");
    var inactive = new CollectionCheque.Drawer(DrawerKind.OWN, retiredId, "Ava Pryce");
    assertThat(
            resolver.forDrawerCorrection(
                cheque(active),
                new CollectionRequests.DrawerCorrection(DrawerKind.OWN, listedId, null)))
        .isEqualTo(new CollectionCheque.Drawer(DrawerKind.OWN, listedId, "Oliver Hartley"));
    assertThat(
            resolver.forDrawerCorrection(
                cheque(inactive),
                new CollectionRequests.DrawerCorrection(DrawerKind.OWN, retiredId, null)))
        .isEqualTo(inactive);
  }

  private record TransitionBeforeDrawerCorrection(
      UUID operationId,
      Long expectedVersion,
      com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction action,
      LocalDate effectiveDate,
      Instant occurredAt,
      String reason,
      String evidence,
      UUID paymentId,
      CollectionRequests.Correction correction,
      List<CollectionRequests.Version> obligations) {}

  @Test
  void anOrdinaryTransitionKeepsItsFingerprintWithoutTheNewPayload() {
    var previous =
        new TransitionBeforeDrawerCorrection(
            UUID.randomUUID(),
            2L,
            com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction.CORRECT,
            LocalDate.of(2026, 11, 15),
            Instant.parse("2026-11-15T12:00:00Z"),
            "Updated evidence",
            "Signed cheque image",
            null,
            correction(null, null, null),
            null);
    var current =
        new CollectionRequests.Transition(
            previous.operationId(),
            previous.expectedVersion(),
            previous.action(),
            previous.effectiveDate(),
            previous.occurredAt(),
            previous.reason(),
            previous.evidence(),
            previous.paymentId(),
            previous.correction(),
            previous.obligations());
    assertThat(CanonicalJsonFingerprint.of(current))
        .isEqualTo(CanonicalJsonFingerprint.of(previous));
  }

  @Test
  void drawerCorrectionRejectsUnexpectedJsonEvenWhenTheMapperIgnoresUnknownFields() {
    var mapper =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .disable(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    """
                    {"drawerKind":"ENDORSED","drawerName":"Calder Mills","amount":"1000.00"}
                    """,
                    CollectionRequests.DrawerCorrection.class))
        .hasMessageContaining("Unexpected drawer correction field: amount");
  }

  /** The receive request as stored before tagging (PAYMENT-PLAN-7), for the fingerprint check. */
  private record ReceiveBeforeTagging(
      UUID operationId,
      UUID partnerId,
      String currency,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
      LocalDate receivedOn,
      LocalDate dueOn,
      String instrumentReference,
      String evidence,
      Instant occurredAt,
      String reason,
      List<CollectionRequests.Application> applications) {}

  /** The correction as stored before tagging. */
  private record CorrectionBeforeTagging(
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
      LocalDate receivedOn,
      LocalDate dueOn,
      String instrumentReference) {}

  /**
   * R16, B11, B20: a request without the new fields has the fingerprint it had before this slice,
   * so a receipt or correction stored before the deploy still replays; with a kind it differs.
   */
  @Test
  void requestsWithoutADrawerKeepThePreDeployFingerprint() {
    var now = receive(null, null, null);
    var before =
        new ReceiveBeforeTagging(
            now.operationId(),
            now.partnerId(),
            now.currency(),
            now.amount(),
            now.receivedOn(),
            now.dueOn(),
            now.instrumentReference(),
            now.evidence(),
            now.occurredAt(),
            now.reason(),
            now.applications());
    assertThat(CanonicalJsonFingerprint.of(now)).isEqualTo(CanonicalJsonFingerprint.of(before));
    assertThat(
            CanonicalJsonFingerprint.of(
                new CollectionRequests.ReceiveCheque(
                    now.operationId(),
                    now.partnerId(),
                    now.currency(),
                    now.amount(),
                    now.receivedOn(),
                    now.dueOn(),
                    now.instrumentReference(),
                    now.evidence(),
                    now.occurredAt(),
                    now.reason(),
                    now.applications(),
                    DrawerKind.OWN,
                    null,
                    null)))
        .isNotEqualTo(CanonicalJsonFingerprint.of(before));

    var correction =
        new CollectionRequests.Correction(
            new BigDecimal("3000.00"),
            LocalDate.of(2026, 11, 15),
            LocalDate.of(2027, 1, 15),
            "CH-001");
    assertThat(CanonicalJsonFingerprint.of(correction))
        .isEqualTo(
            CanonicalJsonFingerprint.of(
                new CorrectionBeforeTagging(
                    correction.amount(),
                    correction.receivedOn(),
                    correction.dueOn(),
                    correction.instrumentReference())));
  }

  /** A typed endorsed name is stored in the drawer list's display form. */
  @Test
  void anEndorsedNameIsStoredTidiedAndABlankOneIsRefused() {
    assertThat(
            resolver
                .forReceipt(
                    receive(DrawerKind.ENDORSED, null, "\u00a0 Calder\u00a0\u00a0 Mills \t"))
                .name())
        .isEqualTo("Calder Mills");
    assertThat(resolver.forReceipt(receive(DrawerKind.ENDORSED, null, "Ｃａｌｄｅｒ")).name())
        .isEqualTo("Calder");
    assertThat(code(() -> resolver.forReceipt(receive(DrawerKind.ENDORSED, null, "\u00a0\u2007"))))
        .isEqualTo("CHEQUE_DRAWER_NAME_REQUIRED");
  }
}
