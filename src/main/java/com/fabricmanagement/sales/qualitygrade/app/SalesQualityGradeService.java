package com.fabricmanagement.sales.qualitygrade.app;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.masterdata.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import com.fabricmanagement.sales.common.app.SalesReferenceResolver;
import com.fabricmanagement.sales.qualitygrade.dto.SalesQualityGradeDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesQualityGradeService {

  private final QualityGradeQueryService qualityGradeQueryService;

  public List<SalesQualityGradeDto> listSalesGrades(
      ProductType productType, boolean includeNonSaleable) {
    return qualityGradeQueryService
        .findSalesGradeReferences(productType, includeNonSaleable)
        .stream()
        .map(SalesQualityGradeService::toDto)
        .toList();
  }

  public SalesQualityGradeSnapshot resolveSnapshot(UUID qualityGradeId) {
    if (qualityGradeId == null) {
      return null;
    }
    QualityGradeReference reference =
        SalesReferenceResolver.resolveNewSelection(
            qualityGradeId,
            "Quality grade",
            () -> qualityGradeQueryService.findReferenceById(qualityGradeId),
            ref -> ref.active() && ref.saleable());
    return new SalesQualityGradeSnapshot(
        reference.id(), reference.code(), reference.name(), reference.priceFactor());
  }

  private static SalesQualityGradeDto toDto(QualityGradeReference reference) {
    return new SalesQualityGradeDto(
        reference.id(),
        reference.code(),
        reference.name(),
        reference.priceFactor(),
        reference.saleable());
  }
}
