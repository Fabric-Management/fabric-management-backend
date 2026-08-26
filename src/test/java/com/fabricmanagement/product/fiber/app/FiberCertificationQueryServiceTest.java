package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.product.fiber.infra.repository.FiberCertificationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FiberCertificationQueryServiceTest {

  @Mock private FiberCertificationRepository repository;
  @InjectMocks private FiberCertificationQueryService queryService;

  @Test
  void findActiveEntityByIdDelegatesToActiveReferenceQuery() {
    UUID certificationId = UUID.randomUUID();
    FiberCertification certification = org.mockito.Mockito.mock(FiberCertification.class);
    when(repository.findByIdAndIsActiveTrue(certificationId))
        .thenReturn(Optional.of(certification));

    assertThat(queryService.findActiveEntityById(certificationId)).contains(certification);
  }
}
