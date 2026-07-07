package com.fabricmanagement.sales.color.app;

import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService.ColorReference;
import com.fabricmanagement.sales.color.dto.SalesColorDto;
import com.fabricmanagement.sales.common.app.SalesReferenceResolver;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesColorService {

  private final ColorQueryService colorQueryService;

  public List<SalesColorDto> listSalesColors(boolean includeInactive) {
    return colorQueryService.findSalesColorReferences(includeInactive).stream()
        .map(
            ref ->
                new SalesColorDto(ref.id(), ref.code(), ref.name(), ref.colorHex(), ref.active()))
        .toList();
  }

  public SalesColorSnapshot resolveNewSelectionSnapshot(UUID colorId) {
    if (colorId == null) {
      return null;
    }

    ColorReference ref =
        SalesReferenceResolver.resolveNewSelection(
            colorId,
            "Color",
            () -> colorQueryService.findReferenceById(colorId),
            ColorReference::active);
    return toSnapshot(ref);
  }

  public SalesColorSnapshot resolveUpdateSnapshot(UUID submittedColorId, QuoteLine existingLine) {
    if (submittedColorId == null) {
      return null;
    }
    if (submittedColorId.equals(existingLine.getColorId())) {
      return new SalesColorSnapshot(
          existingLine.getColorId(),
          existingLine.getColorCode(),
          existingLine.getColorName(),
          existingLine.getColorHex());
    }
    return resolveNewSelectionSnapshot(submittedColorId);
  }

  private SalesColorSnapshot toSnapshot(ColorReference ref) {
    return new SalesColorSnapshot(ref.id(), ref.code(), ref.name(), ref.colorHex());
  }
}
