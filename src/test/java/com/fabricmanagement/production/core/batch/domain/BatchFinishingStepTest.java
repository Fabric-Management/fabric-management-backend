package com.fabricmanagement.production.core.batch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchFinishingStepTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID BATCH = UUID.randomUUID();
  private static final UUID PROCESS = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();
  private static final LocalDate SENT = LocalDate.of(2026, 10, 1);
  private static final LocalDate BACK = LocalDate.of(2026, 10, 9);

  private static BatchFinishingStep sent() {
    return BatchFinishingStep.send(
        TENANT, BATCH, 1, PROCESS, PARTNER, "  DH-4471 ", new BigDecimal("1000"), SENT, null, null);
  }

  @Test
  void aSentStepIsOutAndKeepsTheFirmsJobNumberTrimmed() {
    BatchFinishingStep step = sent();

    assertThat(step.getStatus()).isEqualTo(BatchFinishingStepStatus.SENT);
    assertThat(step.isOut()).isTrue();
    assertThat(step.getFirmJobNumber()).isEqualTo("DH-4471");
    assertThat(step.getSentMetres()).isEqualByComparingTo("1000");
    assertThat(step.getReturnedMetres()).isNull();
    assertThat(step.lossMetres()).isNull();
  }

  @Test
  void receivingRecordsWhatCameBackAndTheLoss() {
    BatchFinishingStep step = sent();

    step.receive(new BigDecimal("968.5"), BACK, null, new BigDecimal("1250.50"), "gbp", null);

    assertThat(step.getStatus()).isEqualTo(BatchFinishingStepStatus.DONE);
    assertThat(step.isDone()).isTrue();
    assertThat(step.getReturnedMetres()).isEqualByComparingTo("968.5");
    assertThat(step.lossMetres()).isEqualByComparingTo("31.5");
    assertThat(step.getCostAmount()).isEqualByComparingTo("1250.50");
    assertThat(step.getCostCurrency()).isEqualTo("GBP");
    assertThat(step.getFirmJobNumber()).isEqualTo("DH-4471");
  }

  @Test
  void fabricThatCameBackLongerHasANegativeLoss() {
    BatchFinishingStep step = sent();

    step.receive(new BigDecimal("1004"), BACK, null, null, null, null);

    assertThat(step.lossMetres()).isEqualByComparingTo("-4");
  }

  @Test
  void theFirmCanGiveItsJobNumberOnReturn() {
    BatchFinishingStep step =
        BatchFinishingStep.send(
            TENANT, BATCH, 1, PROCESS, PARTNER, null, new BigDecimal("500"), SENT, null, null);

    step.receive(new BigDecimal("495"), BACK, " J-88 ", null, null, null);

    assertThat(step.getFirmJobNumber()).isEqualTo("J-88");
  }

  @Test
  void aStepCannotBeReceivedTwice() {
    BatchFinishingStep step = sent();
    step.receive(new BigDecimal("990"), BACK, null, null, null, null);

    assertThatThrownBy(() -> step.receive(new BigDecimal("990"), BACK, null, null, null, null))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("DONE");
  }

  @Test
  void aStepCannotReturnBeforeItWasSent() {
    BatchFinishingStep step = sent();

    assertThatThrownBy(
            () -> step.receive(new BigDecimal("990"), SENT.minusDays(1), null, null, null, null))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("before it was sent");
  }

  @Test
  void aCostNeedsBothAmountAndCurrency() {
    BatchFinishingStep amountOnly = sent();
    BatchFinishingStep currencyOnly = sent();

    assertThatThrownBy(
            () -> amountOnly.receive(new BigDecimal("990"), BACK, null, BigDecimal.TEN, null, null))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("amount and a currency");
    assertThatThrownBy(
            () -> currencyOnly.receive(new BigDecimal("990"), BACK, null, null, "GBP", null))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("amount and a currency");
  }

  @Test
  void aCostIsNotNegativeAndHasAtMostTwoDecimals() {
    BatchFinishingStep negative = sent();
    BatchFinishingStep tooPrecise = sent();

    assertThatThrownBy(
            () ->
                negative.receive(
                    new BigDecimal("990"), BACK, null, new BigDecimal("-1"), "GBP", null))
        .isInstanceOf(BatchDomainException.class);
    assertThatThrownBy(
            () ->
                tooPrecise.receive(
                    new BigDecimal("990"), BACK, null, new BigDecimal("1.234"), "GBP", null))
        .isInstanceOf(BatchDomainException.class);
  }

  @Test
  void metresArePositiveWithAtMostThreeDecimals() {
    assertThatThrownBy(
            () ->
                BatchFinishingStep.send(
                    TENANT, BATCH, 1, PROCESS, null, null, BigDecimal.ZERO, SENT, null, null))
        .isInstanceOf(BatchDomainException.class);
    assertThatThrownBy(
            () ->
                BatchFinishingStep.send(
                    TENANT,
                    BATCH,
                    1,
                    PROCESS,
                    null,
                    null,
                    new BigDecimal("1.2345"),
                    SENT,
                    null,
                    null))
        .isInstanceOf(BatchDomainException.class);
    BatchFinishingStep step = sent();
    assertThatThrownBy(() -> step.receive(new BigDecimal("-5"), BACK, null, null, null, null))
        .isInstanceOf(BatchDomainException.class);
  }

  @Test
  void aJobNumberHasALimit() {
    assertThatThrownBy(
            () ->
                BatchFinishingStep.send(
                    TENANT,
                    BATCH,
                    1,
                    PROCESS,
                    null,
                    "J".repeat(101),
                    BigDecimal.TEN,
                    SENT,
                    null,
                    null))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("100");
  }

  @Test
  void cancellingNeedsAReasonAndOnlyWorksWhileTheLotIsOut() {
    BatchFinishingStep blank = sent();
    assertThatThrownBy(() -> blank.cancel("  "))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("reason");

    BatchFinishingStep step = sent();
    step.cancel(" Entered on the wrong lot ");
    assertThat(step.getStatus()).isEqualTo(BatchFinishingStepStatus.CANCELLED);
    assertThat(step.getNote()).isEqualTo("Entered on the wrong lot");
    assertThatThrownBy(() -> step.receive(new BigDecimal("990"), BACK, null, null, null, null))
        .isInstanceOf(BatchDomainException.class);

    BatchFinishingStep done = sent();
    done.receive(new BigDecimal("990"), BACK, null, null, null, null);
    assertThatThrownBy(() -> done.cancel("too late")).isInstanceOf(BatchDomainException.class);
  }

  @Test
  void aStepFinishedBeforeTheLotCameToUsIsRecordedAsDone() {
    BatchFinishingStep step =
        BatchFinishingStep.recordDone(
            TENANT,
            BATCH,
            2,
            PROCESS,
            null,
            "SUP-7",
            new BigDecimal("2000"),
            SENT,
            new BigDecimal("1980"),
            BACK,
            UUID.randomUUID(),
            null,
            null,
            "Bought dyed");

    assertThat(step.isDone()).isTrue();
    assertThat(step.getSequence()).isEqualTo(2);
    assertThat(step.getPartnerId()).isNull();
    assertThat(step.lossMetres()).isEqualByComparingTo("20");
  }

  @Test
  void aStepNeedsItsLotAndProcess() {
    assertThatThrownBy(
            () ->
                BatchFinishingStep.send(
                    TENANT, null, 1, PROCESS, null, null, BigDecimal.TEN, SENT, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                BatchFinishingStep.send(
                    TENANT, BATCH, 0, PROCESS, null, null, BigDecimal.TEN, SENT, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theFirmsPriceClassCanBeRecordedWhileTheLotIsOut() {
    BatchFinishingStep step = sent();
    UUID dark = UUID.randomUUID();

    assertThat(step.getPriceClassId()).isNull();
    step.assignPriceClass(dark);

    assertThat(step.getPriceClassId()).isEqualTo(dark);
  }

  @Test
  void thePriceClassCannotBeChangedOnceTheLotIsBack() {
    BatchFinishingStep step = sent();
    step.receive(new BigDecimal("990"), BACK, null, null, null, null);

    assertThatThrownBy(() -> step.assignPriceClass(UUID.randomUUID()))
        .isInstanceOf(BatchDomainException.class);
  }
}
