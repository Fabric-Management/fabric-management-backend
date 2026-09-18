package com.fabricmanagement.sales.sample.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.sample.domain.DeliveryMethod;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.infra.repository.SampleDeliveryRepository;
import com.fabricmanagement.sales.sample.infra.repository.SampleRequestRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class SampleManagementServiceTest {

  @Mock private SampleRequestRepository requests;
  @Mock private SampleDeliveryRepository deliveries;
  @Mock private SampleAccessPolicy accessPolicy;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private SampleManagementService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    service = new SampleManagementService(requests, deliveries, accessPolicy);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void deniedDispatchDoesNotMutateRequestOrCreateDelivery() {
    UUID requestId = UUID.randomUUID();
    SampleRequest request = new SampleRequest();
    request.setTenantId(tenantId);
    when(requests.findByTenantIdAndIdAndIsActiveTrue(tenantId, requestId))
        .thenReturn(Optional.of(request));
    when(accessPolicy.canWrite(tenantId, userId, request)).thenReturn(false);

    assertThatThrownBy(
            () -> service.dispatchSample(requestId, DeliveryMethod.CARGO, null, null, null, userId))
        .isInstanceOf(AccessDeniedException.class);

    verify(requests, never()).save(request);
    verify(deliveries, never()).save(org.mockito.ArgumentMatchers.any(SampleDelivery.class));
  }

  @Test
  void deliveryAuthorizationUsesTheParentRequest() {
    UUID deliveryId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    SampleDelivery delivery = new SampleDelivery();
    delivery.setTenantId(tenantId);
    delivery.setSampleRequestId(requestId);
    SampleRequest request = new SampleRequest();
    request.setTenantId(tenantId);
    when(deliveries.findByTenantIdAndIdAndIsActiveTrue(tenantId, deliveryId))
        .thenReturn(Optional.of(delivery));
    when(requests.findByTenantIdAndIdAndIsActiveTrue(tenantId, requestId))
        .thenReturn(Optional.of(request));
    when(accessPolicy.canWrite(tenantId, userId, request)).thenReturn(false);

    assertThatThrownBy(() -> service.markAsDelivered(deliveryId, "Recipient", null, userId))
        .isInstanceOf(AccessDeniedException.class);

    verify(deliveries, never()).save(delivery);
    verify(requests, never()).save(request);
  }
}
