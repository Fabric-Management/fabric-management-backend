package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaygroundTTLReaper (Unit Test)")
class PlaygroundTTLReaperServiceTest {

  @Mock private TenantRepository tenantRepository;

  @InjectMocks private PlaygroundTTLReaperService reaper;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(reaper, "ttlDays", 14);
  }

  @Nested
  @DisplayName("reapExpiredPlaygrounds")
  class ReapExpiredPlaygrounds {

    @Test
    @DisplayName("Should soft-delete expired playgrounds and verify threshold")
    void shouldSoftDeleteExpiredPlaygrounds() {
      Tenant expiredTenant =
          Tenant.create("Playground 1", "PG-1", "pg-1", null, TenantType.PLAYGROUND);
      ReflectionTestUtils.setField(expiredTenant, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(
          expiredTenant, "createdAt", Instant.now().minusSeconds(15 * 86400));

      ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
      when(tenantRepository.findExpiredPlaygrounds(
              eq(TenantType.PLAYGROUND), thresholdCaptor.capture()))
          .thenReturn(List.of(expiredTenant));

      reaper.reapExpiredPlaygrounds();

      assertThat(expiredTenant.getIsActive()).isFalse();

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Tenant>> listCaptor = ArgumentCaptor.forClass(List.class);
      verify(tenantRepository).saveAll(listCaptor.capture());

      List<Tenant> savedTenants = listCaptor.getValue();
      assertThat(savedTenants).hasSize(1);
      assertThat(savedTenants.get(0)).isEqualTo(expiredTenant);

      Instant actualThreshold = thresholdCaptor.getValue();
      Instant expectedThreshold = Instant.now().minus(14, ChronoUnit.DAYS);
      assertThat(actualThreshold)
          .isBetween(expectedThreshold.minusSeconds(5), expectedThreshold.plusSeconds(5));
    }

    @Test
    @DisplayName("Should do nothing if no expired playgrounds found")
    void shouldDoNothingIfNoExpiredPlaygrounds() {
      when(tenantRepository.findExpiredPlaygrounds(eq(TenantType.PLAYGROUND), any(Instant.class)))
          .thenReturn(Collections.emptyList());

      reaper.reapExpiredPlaygrounds();

      verify(tenantRepository)
          .findExpiredPlaygrounds(eq(TenantType.PLAYGROUND), any(Instant.class));
      verifyNoMoreInteractions(tenantRepository);
    }
  }
}
