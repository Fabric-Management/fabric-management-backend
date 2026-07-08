package com.fabricmanagement.sales.qualitygrade.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesQualityGradeServiceTest {

  @Mock private QualityGradeQueryService qualityGradeQueryService;

  @InjectMocks private SalesQualityGradeService salesQualityGradeService;

  @Test
  @DisplayName("Should resolve active saleable quality grade as quote line snapshot")
  void shouldResolveActiveSaleableQualityGradeAsSnapshot() {
    UUID qualityGradeId = UUID.randomUUID();
    when(qualityGradeQueryService.findReferenceById(qualityGradeId))
        .thenReturn(
            Optional.of(
                new QualityGradeReference(
                    qualityGradeId, "A", "Grade A", new BigDecimal("1.125"), true, true)));

    SalesQualityGradeSnapshot snapshot = salesQualityGradeService.resolveSnapshot(qualityGradeId);

    assertEquals(qualityGradeId, snapshot.id());
    assertEquals("A", snapshot.code());
    assertEquals("Grade A", snapshot.name());
    assertEquals(0, snapshot.priceFactor().compareTo(new BigDecimal("1.125")));
  }

  @Test
  @DisplayName("Should reject newly selected inactive quality grade with explicit domain error")
  void shouldRejectNewlySelectedInactiveQualityGrade() {
    UUID qualityGradeId = UUID.randomUUID();
    when(qualityGradeQueryService.findReferenceById(qualityGradeId))
        .thenReturn(
            Optional.of(
                new QualityGradeReference(
                    qualityGradeId, "A", "Grade A", new BigDecimal("1.125"), true, false)));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class,
            () -> salesQualityGradeService.resolveSnapshot(qualityGradeId));

    assertEquals("SALES_015_REFERENCE_NO_LONGER_AVAILABLE", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  @DisplayName("Should reject newly selected non-saleable quality grade with explicit domain error")
  void shouldRejectNewlySelectedNonSaleableQualityGrade() {
    UUID qualityGradeId = UUID.randomUUID();
    when(qualityGradeQueryService.findReferenceById(qualityGradeId))
        .thenReturn(
            Optional.of(
                new QualityGradeReference(
                    qualityGradeId, "A", "Grade A", new BigDecimal("1.125"), false, true)));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class,
            () -> salesQualityGradeService.resolveSnapshot(qualityGradeId));

    assertEquals("SALES_015_REFERENCE_NO_LONGER_AVAILABLE", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
  }

  @Test
  @DisplayName("Should return 404 for unknown or cross-tenant quality grade")
  void shouldReturn404ForUnknownQualityGrade() {
    UUID qualityGradeId = UUID.randomUUID();
    when(qualityGradeQueryService.findReferenceById(qualityGradeId)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class, () -> salesQualityGradeService.resolveSnapshot(qualityGradeId));
  }

  @Test
  @DisplayName("Should accept existing inactive quality grade echo on full-state update")
  void shouldAcceptExistingInactiveQualityGradeEchoOnFullStateUpdate() {
    UUID qualityGradeId = UUID.randomUUID();
    QuoteLine line = new QuoteLine();
    line.applyQualityGrade(qualityGradeId, "A", "Grade A", new BigDecimal("1.125"));

    SalesQualityGradeSnapshot snapshot =
        salesQualityGradeService.resolveUpdateSnapshot(qualityGradeId, line);

    assertEquals(qualityGradeId, snapshot.id());
    assertEquals("A", snapshot.code());
    assertEquals("Grade A", snapshot.name());
    assertEquals(0, snapshot.priceFactor().compareTo(new BigDecimal("1.125")));
    verifyNoInteractions(qualityGradeQueryService);
  }
}
