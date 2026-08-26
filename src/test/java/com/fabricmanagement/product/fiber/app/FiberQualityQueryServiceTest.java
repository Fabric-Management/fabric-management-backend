package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberQualityQueryServiceTest {

  @Mock private FiberRepository fiberRepository;
  @Mock private FiberQualityStandardRepository qualityStandardRepository;
  @InjectMocks private FiberQualityQueryService queryService;

  @Test
  void findByProductIdOrIdReturnsProductMatchWithoutFallback() {
    UUID productId = UUID.randomUUID();
    Fiber fiber = org.mockito.Mockito.mock(Fiber.class);
    when(fiberRepository.findByProductId(productId)).thenReturn(Optional.of(fiber));

    assertThat(queryService.findByProductIdOrId(productId)).contains(fiber);
    verify(fiberRepository, never()).findById(productId);
  }

  @Test
  void findByProductIdOrIdPreservesLegacyIdFallback() {
    UUID productId = UUID.randomUUID();
    Fiber fiber = org.mockito.Mockito.mock(Fiber.class);
    when(fiberRepository.findByProductId(productId)).thenReturn(Optional.empty());
    when(fiberRepository.findById(productId)).thenReturn(Optional.of(fiber));

    assertThat(queryService.findByProductIdOrId(productId)).contains(fiber);
  }

  @Test
  void qualityStandardQueriesDelegateWithTenantScope() {
    UUID tenantId = UUID.randomUUID();
    UUID standardId = UUID.randomUUID();
    UUID isoCodeId = UUID.randomUUID();
    FiberQualityStandard standard = org.mockito.Mockito.mock(FiberQualityStandard.class);
    when(qualityStandardRepository.findByTenantIdAndId(tenantId, standardId))
        .thenReturn(Optional.of(standard));
    when(qualityStandardRepository.findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
            tenantId, isoCodeId))
        .thenReturn(Optional.of(standard));

    assertThat(queryService.findQualityStandardById(tenantId, standardId)).contains(standard);
    assertThat(queryService.findDefaultQualityStandard(tenantId, isoCodeId)).contains(standard);
  }
}
