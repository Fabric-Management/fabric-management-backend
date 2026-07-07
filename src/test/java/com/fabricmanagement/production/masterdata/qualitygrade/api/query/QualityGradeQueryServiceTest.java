package com.fabricmanagement.production.masterdata.qualitygrade.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.production.masterdata.qualitygrade.infra.repository.QualityGradeRepository;
import java.math.BigDecimal;
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
class QualityGradeQueryServiceTest {

  @Mock private QualityGradeRepository qualityGradeRepository;

  @InjectMocks private QualityGradeQueryService qualityGradeQueryService;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should expose saleable grades by default sorted by rank")
  void shouldExposeSaleableGradesByDefaultSortedByRank() {
    QualityGrade waste = grade("WST", "Waste", 5, "0.000", false);
    QualityGrade first = grade("1A", "First Quality", 1, "1.000", true);
    QualityGrade second = grade("2", "Second Quality", 3, "0.800", true);

    when(qualityGradeRepository.findByTenantIdAndProductTypeAndIsActiveTrue(
            tenantId, ProductType.FABRIC))
        .thenReturn(List.of(waste, second, first));

    List<QualityGradeQueryService.QualityGradeReference> result =
        qualityGradeQueryService.findSalesGradeReferences(ProductType.FABRIC, false);

    assertEquals(List.of("1A", "2"), result.stream().map(r -> r.code()).toList());
  }

  @Test
  @DisplayName("Should include non-saleable grades only when explicitly requested")
  void shouldIncludeNonSaleableGradesOnlyWhenExplicitlyRequested() {
    QualityGrade waste = grade("WST", "Waste", 5, "0.000", false);
    QualityGrade first = grade("1A", "First Quality", 1, "1.000", true);

    when(qualityGradeRepository.findByTenantIdAndProductTypeAndIsActiveTrue(
            tenantId, ProductType.FABRIC))
        .thenReturn(List.of(waste, first));

    List<QualityGradeQueryService.QualityGradeReference> result =
        qualityGradeQueryService.findSalesGradeReferences(ProductType.FABRIC, true);

    assertEquals(List.of("1A", "WST"), result.stream().map(r -> r.code()).toList());
  }

  @Test
  @DisplayName("Should resolve active grade references within current tenant")
  void shouldResolveActiveGradeReferencesWithinCurrentTenant() {
    QualityGrade grade = grade("1A", "First Quality", 1, "1.000", true);

    when(qualityGradeRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, grade.getId()))
        .thenReturn(Optional.of(grade));

    Optional<QualityGradeQueryService.QualityGradeReference> result =
        qualityGradeQueryService.findActiveReferenceById(grade.getId());

    assertTrue(result.isPresent());
    assertEquals("1A", result.get().code());
    assertEquals("First Quality", result.get().name());
    assertEquals(0, result.get().priceFactor().compareTo(new BigDecimal("1.000")));
    assertTrue(result.get().saleable());
  }

  private QualityGrade grade(
      String code, String name, int rank, String priceFactor, boolean saleable) {
    return QualityGrade.create(
        tenantId,
        ProductType.FABRIC,
        code,
        name,
        rank,
        new BigDecimal(priceFactor),
        saleable,
        false,
        null,
        rank == 1);
  }
}
