package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationCertificationRepository;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerCertificationRepository;
import com.fabricmanagement.production.common.exception.BatchCertificationOverlapException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchCertification;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.execution.batch.dto.AddBatchCertificationRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchCertificationService")
class BatchCertificationServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();
  private static final UUID CERT_ID = UUID.randomUUID();

  @Mock private BatchCertificationRepository certificationRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private FiberCertificationRepository fiberCertificationRepository;
  @Mock private TradingPartnerCertificationRepository partnerCertificationRepository;
  @Mock private OrganizationCertificationRepository orgCertificationRepository;

  @InjectMocks private BatchCertificationService batchCertificationService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("add() throws NotFoundException when certification type is not active")
  void add_withInactiveCertification_throwsNotFoundException() {
    Batch batch = mock(Batch.class);
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(fiberCertificationRepository.findByIdAndIsActiveTrue(CERT_ID))
        .thenReturn(Optional.empty());

    AddBatchCertificationRequest request =
        AddBatchCertificationRequest.builder().certificationId(CERT_ID).build();

    assertThatThrownBy(() -> batchCertificationService.add(BATCH_ID, request))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Certification type is not active");
  }

  @Test
  @DisplayName(
      "add() throws BatchCertificationOverlapException when validity period overlaps existing")
  void add_overlappingValidityPeriod_throwsBatchCertificationOverlapException() {
    Batch batch = mock(Batch.class);
    when(batch.getId()).thenReturn(BATCH_ID);
    FiberCertification cert = mock(FiberCertification.class);
    when(cert.getId()).thenReturn(CERT_ID);

    BatchCertification existing = mock(BatchCertification.class);
    when(existing.getValidFrom()).thenReturn(null);
    when(existing.getValidUntil()).thenReturn(null);

    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(fiberCertificationRepository.findByIdAndIsActiveTrue(CERT_ID))
        .thenReturn(Optional.of(cert));
    when(certificationRepository.findActiveByBatchAndCertAndScopeAndPartnerAndOrgExcludingId(
            BATCH_ID, CERT_ID, BatchCertificationScope.BATCH, null, null, null))
        .thenReturn(List.of(existing));

    AddBatchCertificationRequest request =
        AddBatchCertificationRequest.builder().certificationId(CERT_ID).build();

    assertThatThrownBy(() -> batchCertificationService.add(BATCH_ID, request))
        .isInstanceOf(BatchCertificationOverlapException.class)
        .hasMessageContaining("overlaps with the given dates");
  }
}
