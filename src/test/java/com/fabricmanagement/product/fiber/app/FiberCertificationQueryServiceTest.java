package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.product.fiber.infra.repository.FiberCertificationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Certification schemes are one shared dictionary owned by the catalogue (FIBER-CATALOG-1). */
@ExtendWith(MockitoExtension.class)
class FiberCertificationQueryServiceTest {

  @Mock private FiberCertificationRepository repository;
  @InjectMocks private FiberCertificationQueryService queryService;

  @Test
  void findActiveEntityByIdIsScopedToTheCatalogueOwner() {
    UUID certificationId = UUID.randomUUID();
    FiberCertification certification = org.mockito.Mockito.mock(FiberCertification.class);
    when(repository.findByTenantIdAndIdAndIsActiveTrue(FiberCatalog.OWNER_ID, certificationId))
        .thenReturn(Optional.of(certification));

    assertThat(queryService.findActiveEntityById(certificationId)).contains(certification);
  }

  @Test
  void findActiveEntityByCodeIsScopedToTheCatalogueOwner() {
    FiberCertification certification = org.mockito.Mockito.mock(FiberCertification.class);
    when(repository.findByTenantIdAndCertificationCodeAndIsActiveTrue(
            FiberCatalog.OWNER_ID, "GOTS"))
        .thenReturn(Optional.of(certification));

    assertThat(queryService.findActiveEntityByCode("GOTS")).contains(certification);
  }

  @Test
  void nullIdAndEmptyIdSetsNeverQuery() {
    assertThat(queryService.findActiveEntityById(null)).isEmpty();
    assertThat(queryService.findAllActiveByIds(java.util.Set.of())).isEmpty();
    verifyNoInteractions(repository);
  }
}
