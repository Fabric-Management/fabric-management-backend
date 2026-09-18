package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.exception.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RoutingPoolConfigurationIT extends RoutingIntegrationSupport {
  @Test
  void firstWriteAndRevisionConflictAreAtomic() {
    var member = user("Candidate", "flowboard:write", "sales:write");
    configure(member.getId());
    var before = queries.pool(tenant, RoutingPoolKey.ORDER_COVER);
    assertThat(before.configured()).isTrue();
    assertThat(before.revision()).isEqualTo(1);
    assertThat(before.members()).extracting(m -> m.userId()).containsExactly(member.getId());
    assertThatThrownBy(
            () -> configuration.configure(tenant, RoutingPoolKey.ORDER_COVER, 99L, Set.of()))
        .isInstanceOfSatisfying(
            RoutingException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(409));
    assertThat(queries.pool(tenant, RoutingPoolKey.ORDER_COVER)).isEqualTo(before);
  }

  @Test
  void invalidMemberWritesNothingAndReportsPerUserReason() {
    var invalid = user("NoSales", "flowboard:write");
    assertThatThrownBy(() -> configure(invalid.getId()))
        .isInstanceOfSatisfying(
            RoutingMembersRejectedException.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(422);
              assertThat(e.getReasons())
                  .containsEntry(invalid.getId(), List.of(RoutingReason.MISSING_SALES_WRITE));
            });
    assertThat(count("flowboard.routing_pool")).isZero();
    assertThat(count("flowboard.routing_pool_member")).isZero();
  }

  @Test
  void simultaneousFirstWritesCreateOnePool() throws Exception {
    var results = race(this::firstWrite, this::firstWrite);
    assertThat(results).containsExactlyInAnyOrder(true, false);
    assertThat(count("flowboard.routing_pool")).isEqualTo(1);
    assertThat(repository.pool(tenant, RoutingPoolKey.ORDER_COVER).orElseThrow().revision())
        .isEqualTo(1);
  }

  private boolean firstWrite() {
    try {
      configuration.configure(tenant, RoutingPoolKey.ORDER_COVER, null, Set.of());
      return true;
    } catch (RoutingException e) {
      return false;
    }
  }

  @Test
  void tenantCannotSeeOrConfigureAnotherTenantsMember() {
    var member = user("Candidate", "flowboard:write", "sales:write");
    configure(member.getId());
    UUID other = UUID.randomUUID();
    com.fabricmanagement.common.infrastructure.persistence.TenantContext.executeInTenantContext(
        other,
        () -> {
          assertThat(queries.pool(other, RoutingPoolKey.ORDER_COVER).configured()).isFalse();
          assertThatThrownBy(
                  () ->
                      configuration.configure(
                          other, RoutingPoolKey.ORDER_COVER, null, Set.of(member.getId())))
              .isInstanceOf(RoutingMembersRejectedException.class);
        });
    assertThat(queries.pool(tenant, RoutingPoolKey.ORDER_COVER).revision()).isEqualTo(1);
  }

  @Test
  void candidateSearchFiltersBeforePaging() {
    user("Alpha", "flowboard:write", "sales:write");
    user("AlphaTwo", "flowboard:write", "sales:write");
    user("Excluded", "flowboard:write");
    var page =
        queries.candidates(
            tenant,
            RoutingPoolKey.ORDER_COVER,
            "alpha",
            org.springframework.data.domain.PageRequest.of(0, 1));
    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).hasSize(1);
  }
}
