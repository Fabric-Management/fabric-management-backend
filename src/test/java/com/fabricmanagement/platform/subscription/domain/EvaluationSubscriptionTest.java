package com.fabricmanagement.platform.subscription.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class EvaluationSubscriptionTest {
  @Test
  void learningAccessSurvivesTheOldTrialDeadlineButPausingRevokesIt() {
    Instant signup = Instant.parse("2026-10-25T10:00:00Z");
    var row =
        Subscription.builder().osCode("FabricOS").osName("FabricOS").startDate(signup).build();
    row.selectEvaluation(true, null, "SERVER_DEFAULT");
    assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
    assertThat(row.getTrialEndsAt()).isNull();
    assertThat(row.isActiveAt(signup.plus(365, ChronoUnit.DAYS))).isTrue();
    row.deselectEvaluation();
    assertThat(row.isActiveAt(signup)).isFalse();
    assertThatThrownBy(row::resume).isInstanceOf(PlatformDomainException.class);
    row.selectEvaluation(true, null, "OWNER");
    assertThat(row.isActiveAt(signup.plus(365, ChronoUnit.DAYS))).isTrue();
  }

  @Test
  void realTrialExpiresAtItsDeadlineAndPaidActivationClearsEvaluationPrivileges() {
    Instant end = Instant.now().plus(90, ChronoUnit.DAYS);
    var row =
        Subscription.builder()
            .osCode("FabricOS")
            .osName("FabricOS")
            .startDate(end.minusSeconds(1))
            .build();
    row.selectEvaluation(false, end, "OWNER");
    assertThat(row.isActiveAt(end.minusSeconds(1))).isTrue();
    assertThat(row.isActiveAt(end)).isFalse();
    row.activate();
    assertThat(row.isEvaluationManaged()).isFalse();
    assertThat(row.isLearningAccess()).isFalse();
    assertThat(row.getTrialEndsAt()).isNull();
    assertThatThrownBy(() -> row.selectEvaluation(true, null, "OWNER"))
        .isInstanceOf(PlatformDomainException.class);
  }

  @Test
  void cancelledHistoryCannotBeReopenedAsEvaluation() {
    var row =
        Subscription.builder()
            .osCode("FabricOS")
            .osName("FabricOS")
            .startDate(Instant.now())
            .build();
    row.cancel();
    assertThatThrownBy(() -> row.selectEvaluation(true, null, "OWNER"))
        .isInstanceOf(PlatformDomainException.class);
    assertThat(row.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
  }
}
