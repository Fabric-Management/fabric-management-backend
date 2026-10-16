package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/**
 * CONSIGNEE-1 C6: {@code sales:verify-delivery} is held by planning, logistics and sales leads by
 * tenant role setup, never by workers, and is not a by-product of {@code sales:write}.
 */
class DeliveryVerificationGrantsTest {

  private static List<PermissionTemplate> verifyRows() {
    return new PermissionTemplateSeeder(null, null)
        .buildDesiredTemplates().stream()
            .filter(row -> "sales".equals(row.getResource()))
            .filter(row -> "verify-delivery".equals(row.getAction()))
            .toList();
  }

  @Test
  void leadsOfPlanningLogisticsAndSalesHoldIt() {
    assertThat(verifyRows())
        .extracting(
            PermissionTemplate::getRoleCode,
            PermissionTemplate::getDepartmentCode,
            PermissionTemplate::getDataScope)
        .containsExactlyInAnyOrder(
            Tuple.tuple("ADMIN", null, DataScope.GLOBAL),
            Tuple.tuple("SUPERVISOR", "PLANNING", DataScope.DEPARTMENT),
            Tuple.tuple("MANAGER", "PLANNING", DataScope.ORGANIZATION),
            Tuple.tuple("SUPERVISOR", "SHIPPING", DataScope.DEPARTMENT),
            Tuple.tuple("MANAGER", "SHIPPING", DataScope.ORGANIZATION),
            Tuple.tuple("MANAGER", "WAREHOUSE", DataScope.ORGANIZATION),
            Tuple.tuple("SUPERVISOR", "SALES", DataScope.DEPARTMENT),
            Tuple.tuple("MANAGER", "SALES", DataScope.ORGANIZATION));
  }

  @Test
  void noWorkerHoldsIt() {
    assertThat(verifyRows()).noneMatch(row -> "WORKER".equals(row.getRoleCode()));
  }

  @Test
  void holdingSalesWriteDoesNotImplyIt() {
    List<PermissionTemplate> all = new PermissionTemplateSeeder(null, null).buildDesiredTemplates();
    List<PermissionTemplate> verify = verifyRows();

    // A sales worker writes orders (OWN) but cannot confirm their delivery details.
    assertThat(all)
        .anyMatch(
            row ->
                "WORKER".equals(row.getRoleCode())
                    && "SALES".equals(row.getDepartmentCode())
                    && "sales".equals(row.getResource())
                    && "write".equals(row.getAction()));
    assertThat(verify)
        .noneMatch(
            row -> "WORKER".equals(row.getRoleCode()) && "SALES".equals(row.getDepartmentCode()));
  }
}
