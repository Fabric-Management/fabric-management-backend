package com.fabricmanagement.production.masterdata.color.api.query;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public read contract for other modules that need color-card references. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ColorQueryService {

  private final ColorRepository colorRepository;

  public List<ColorReference> findSalesColorReferences(boolean includeInactive) {
    UUID tenantId = TenantContext.requireTenantId();
    return (includeInactive
            ? colorRepository.findByTenantIdOrderByCode(tenantId)
            : colorRepository.findByTenantIdAndIsActiveTrueOrderByCode(tenantId))
        .stream().map(ColorReference::from).toList();
  }

  public Optional<ColorReference> findReferenceById(UUID colorId) {
    UUID tenantId = TenantContext.requireTenantId();
    return colorRepository.findByTenantIdAndId(tenantId, colorId).map(ColorReference::from);
  }

  public Optional<ColorReference> findReferenceByCode(String code) {
    UUID tenantId = TenantContext.requireTenantId();
    return colorRepository.findByTenantIdAndCode(tenantId, code).map(ColorReference::from);
  }

  public Optional<ColorReference> findActiveReferenceById(UUID colorId) {
    UUID tenantId = TenantContext.requireTenantId();
    return colorRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, colorId)
        .map(ColorReference::from);
  }

  public record ColorReference(UUID id, String code, String name, String colorHex, boolean active) {
    static ColorReference from(Color color) {
      return new ColorReference(
          color.getId(),
          color.getCode(),
          color.getName(),
          color.getColorHex(),
          Boolean.TRUE.equals(color.getIsActive()));
    }
  }
}
