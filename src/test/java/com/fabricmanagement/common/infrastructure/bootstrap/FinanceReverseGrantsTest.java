package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/**
 * {@code finance:reverse} (void or reverse a payment) is held by finance supervisors and managers
 * by tenant role setup, never by workers, and is not a by-product of {@code finance:write}.
 */
class FinanceReverseGrantsTest {

  private static List<PermissionTemplate> templates() {
    return new PermissionTemplateSeeder(null, null).buildDesiredTemplates();
  }

  private static List<PermissionTemplate> reverseRows() {
    return templates().stream()
        .filter(row -> "finance".equals(row.getResource()))
        .filter(row -> "reverse".equals(row.getAction()))
        .toList();
  }

  @Test
  void financeSupervisorsAndManagersHoldIt() {
    assertThat(reverseRows())
        .extracting(
            PermissionTemplate::getRoleCode,
            PermissionTemplate::getDepartmentCode,
            PermissionTemplate::getDataScope)
        .containsExactlyInAnyOrder(
            Tuple.tuple("SUPERVISOR", "FINANCE", DataScope.DEPARTMENT),
            Tuple.tuple("MANAGER", "FINANCE", DataScope.ORGANIZATION));
  }

  @Test
  void noWorkerHoldsIt() {
    assertThat(reverseRows()).noneMatch(row -> "WORKER".equals(row.getRoleCode()));
  }

  @Test
  void aFinanceWorkerEntersPaymentsButCannotCorrectThem() {
    assertThat(templates())
        .anyMatch(
            row ->
                "WORKER".equals(row.getRoleCode())
                    && "FINANCE".equals(row.getDepartmentCode())
                    && "finance".equals(row.getResource())
                    && "write".equals(row.getAction()));
    assertThat(reverseRows())
        .noneMatch(
            row -> "WORKER".equals(row.getRoleCode()) && "FINANCE".equals(row.getDepartmentCode()));
  }

  @Test
  void isNotMirroredIntoCosting() {
    assertThat(templates())
        .noneMatch(row -> "costing".equals(row.getResource()) && "reverse".equals(row.getAction()));
  }
}
