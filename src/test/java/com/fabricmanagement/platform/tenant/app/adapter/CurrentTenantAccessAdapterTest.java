package com.fabricmanagement.platform.tenant.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository.TenantAccessView;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CEDIT-05 review R2: the live channel's fresh tenant access rule. */
class CurrentTenantAccessAdapterTest {

  private final TenantRepository tenants = mock(TenantRepository.class);
  private final CurrentTenantAccessAdapter adapter = new CurrentTenantAccessAdapter(tenants);

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("no bound tenant and the system tenant have no access, without a read")
  void unboundAndSystemTenantsAreRefused() {
    assertThat(adapter.currentTenantHasAccess()).isFalse();

    TenantContext.setCurrentTenantId(TenantContext.SYSTEM_TENANT_ID);
    assertThat(adapter.currentTenantHasAccess()).isFalse();

    verify(tenants, never()).findAccessViewById(any());
  }

  @Test
  @DisplayName("a row the tenant cannot see (missing, or hidden by RLS) has no access")
  void missingRowIsRefused() {
    UUID tenantId = bind();
    when(tenants.findAccessViewById(tenantId)).thenReturn(Optional.empty());

    assertThat(adapter.currentTenantHasAccess()).isFalse();
  }

  @Test
  @DisplayName("an active tenant follows TenantStatus.hasAccess: TRIAL, ACTIVE, EXPIRED in")
  void statusDecidesForActiveTenants() {
    assertAccess(true, TenantStatus.TRIAL, true);
    assertAccess(true, TenantStatus.ACTIVE, true);
    assertAccess(true, TenantStatus.EXPIRED, true);
    assertAccess(true, TenantStatus.SUSPENDED, false);
    assertAccess(true, TenantStatus.CANCELLED, false);
  }

  @Test
  @DisplayName("a deactivated tenant, or one without an active flag or status, has no access")
  void inactiveOrIncompleteTenantsAreRefused() {
    assertAccess(false, TenantStatus.ACTIVE, false);
    assertAccess(null, TenantStatus.ACTIVE, false);
    assertAccess(true, null, false);
  }

  private void assertAccess(Boolean active, TenantStatus status, boolean expected) {
    UUID tenantId = bind();
    when(tenants.findAccessViewById(tenantId)).thenReturn(Optional.of(view(active, status)));

    assertThat(adapter.currentTenantHasAccess())
        .as("active=%s status=%s", active, status)
        .isEqualTo(expected);
  }

  private static UUID bind() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    return tenantId;
  }

  private static TenantAccessView view(Boolean active, TenantStatus status) {
    return new TenantAccessView() {
      @Override
      public Boolean getIsActive() {
        return active;
      }

      @Override
      public TenantStatus getStatus() {
        return status;
      }
    };
  }
}
