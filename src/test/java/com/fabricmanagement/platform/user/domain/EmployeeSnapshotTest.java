package com.fabricmanagement.platform.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeSnapshotTest {

  @Test
  void absentIsNotPresent() {
    assertThat(EmployeeSnapshot.absent().isPresent()).isFalse();
  }

  @Test
  void snapshotWithUserIdIsPresent() {
    UUID uid = UUID.randomUUID();
    assertThat(new EmployeeSnapshot(uid, null, null, null, null, null, null, null).isPresent())
        .isTrue();
  }
}
