package com.fabricmanagement.sales.ownership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerCommercialAssignmentTest {

  @Test
  void closureFillsOnlyClosureProvenanceAndCannotBeRepeated() {
    UUID representativeId = UUID.randomUUID();
    UUID decidingUserId = UUID.randomUUID();
    Instant validFrom = Instant.parse("2026-07-28T09:00:00Z");
    CustomerCommercialAssignment assignment =
        CustomerCommercialAssignment.open(
            UUID.randomUUID(),
            representativeId,
            validFrom,
            AssignmentSource.MANUAL,
            ActorRef.user(decidingUserId),
            "OWNERSHIP_POLICY_V1",
            null);

    assignment.close(
        validFrom.plusSeconds(60),
        AssignmentClosureReason.REPRESENTATIVE_DEACTIVATED,
        ActorRef.system("OWNERSHIP_POLICY"));

    assertThat(assignment.getRepresentativeId()).isEqualTo(representativeId);
    assertThat(assignment.getDecidedByType()).isEqualTo(AssignmentActorType.USER);
    assertThat(assignment.getDecidedByUserId()).isEqualTo(decidingUserId);
    assertThat(assignment.getDecidedBySystemCode()).isNull();
    assertThat(assignment.getClosedByType()).isEqualTo(AssignmentActorType.SYSTEM);
    assertThat(assignment.getClosedByUserId()).isNull();
    assertThat(assignment.getClosedBySystemCode()).isEqualTo("OWNERSHIP_POLICY");

    assertThatThrownBy(
            () ->
                assignment.close(
                    validFrom.plusSeconds(120),
                    AssignmentClosureReason.SUPERSEDED,
                    ActorRef.user(UUID.randomUUID())))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(assignment::delete).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(assignment::activate).isInstanceOf(UnsupportedOperationException.class);
  }
}
