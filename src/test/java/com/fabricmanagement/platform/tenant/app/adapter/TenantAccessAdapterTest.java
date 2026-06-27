package com.fabricmanagement.platform.tenant.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

class TenantAccessAdapterTest {

  private final TenantSystemService tenantSystemService =
      org.mockito.Mockito.mock(TenantSystemService.class);
  private final TenantAccessAdapter adapter = new TenantAccessAdapter(tenantSystemService);

  @Test
  @DisplayName("isWritable follows TenantStatus.canWrite")
  void shouldReturnWritableDecisionFromTenantStatus() {
    assertWritable(TenantStatus.TRIAL, true);
    assertWritable(TenantStatus.ACTIVE, true);
    assertWritable(TenantStatus.EXPIRED, false);
    assertWritable(TenantStatus.SUSPENDED, false);
  }

  @Test
  @DisplayName("unknown tenant fails open")
  void shouldFailOpenForUnknownTenant() {
    UUID tenantId = UUID.randomUUID();
    when(tenantSystemService.findById(tenantId)).thenReturn(Optional.empty());

    assertThat(adapter.isWritable(tenantId)).isTrue();
  }

  @Test
  @DisplayName("isWritable is cacheable")
  void shouldBeCacheable() throws NoSuchMethodException {
    Method method = TenantAccessAdapter.class.getMethod("isWritable", UUID.class);
    Cacheable cacheable = method.getAnnotation(Cacheable.class);

    assertThat(cacheable).isNotNull();
    assertThat(cacheable.value()).containsExactly("tenant-writable");
  }

  private void assertWritable(TenantStatus status, boolean expected) {
    UUID tenantId = UUID.randomUUID();
    when(tenantSystemService.findById(tenantId))
        .thenReturn(Optional.of(TenantDto.builder().id(tenantId).status(status).build()));

    assertThat(adapter.isWritable(tenantId)).isEqualTo(expected);
  }
}
