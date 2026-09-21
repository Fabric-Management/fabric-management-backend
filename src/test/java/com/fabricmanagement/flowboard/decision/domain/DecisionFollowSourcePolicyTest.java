package com.fabricmanagement.flowboard.decision.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.user.domain.SystemUser;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DecisionFollowSourcePolicyTest {
  private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");

  @ParameterizedTest(name = "{0}")
  @MethodSource("settlements")
  void settlementRequiresAHumanUserActor(
      String scenario, String kind, UUID actor, boolean expected) {
    assertThat(DecisionFollowSourcePolicy.acceptsSettlement(kind, actor, SystemUser.ID))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> settlements() {
    return Stream.of(
        Arguments.of("human receipt actor", "USER", USER, true),
        Arguments.of("system actor kind", "SYSTEM", USER, false),
        Arguments.of("reserved system user", "USER", SystemUser.ID, false),
        Arguments.of("null actor", "USER", null, false),
        Arguments.of("null actor kind", null, USER, false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("assignments")
  void assignmentRequiresAHumanUser(String scenario, UUID assignee, boolean expected) {
    assertThat(DecisionFollowSourcePolicy.acceptsAssignment(assignee, SystemUser.ID))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> assignments() {
    return Stream.of(
        Arguments.of("human user assignment", USER, true),
        Arguments.of("reserved system user assignment", SystemUser.ID, false),
        Arguments.of("department assignment without user", null, false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("openings")
  void openingRequiresAnActiveUserOfTheSameTenant(
      String scenario, UUID createdBy, boolean activeTenantUser, boolean expected) {
    assertThat(DecisionFollowSourcePolicy.acceptsOpened(createdBy, activeTenantUser, SystemUser.ID))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> openings() {
    return Stream.of(
        Arguments.of("active user in the tenant", USER, true, true),
        Arguments.of("inactive user in the tenant", USER, false, false),
        Arguments.of("active user in another tenant", USER, false, false),
        Arguments.of("reserved system user", SystemUser.ID, true, false),
        Arguments.of("null creator", null, true, false));
  }
}
